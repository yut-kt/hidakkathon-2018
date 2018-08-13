package hidakkathon2018;

import java.io.*;
import java.util.*;
import java.util.stream.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.commons.io.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.web.servlet.*;
import org.springframework.context.annotation.*;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;
import org.springframework.ui.*;
import org.springframework.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.*;
import org.springframework.web.servlet.*;

import hidakkathon2018.model.*;
import hidakkathon2018.repository.*;

@Controller
@SpringBootApplication
public class App {
	@Autowired
	ArticlesRepository articlesRepository;
	@Autowired
	ResourceLoader resourceLoader;

	@RequestMapping(value = "/", method = RequestMethod.GET)
	String getIndex(Model model, @RequestParam(name = "page", required = false) Integer page, HttpServletResponse res) {
		if (!isAuthenticated()) {
			return "redirect:/login";
		}
		if (page == null) {
			page = 1;
		}
		Integer articleCount = articlesRepository.count();
		int pageSize = 20;
		int maxPage = (int) Math.ceil((double)articleCount / (double)pageSize);
		if (maxPage == 0) {
			maxPage = 1;
		}
		if (page < 1 || maxPage < page) {
			res.setStatus(HttpStatus.BAD_REQUEST.value());
			return "error";
		}
		int offset = (page - 1) * pageSize;

		User user = userRepository.find(currentUser().getId());
		if (user == null) {
			session.invalidate();
			return "redirect:/login";
		}

		List<Article> articles = articlesRepository.findAll(offset, pageSize).stream().peek(r -> {
			r.setUser(userRepository.find(r.getAuthorId()));
			r.setDeltaTime(this.deltaTime(r.getUpdatedAt()));
			r.setIineCount(iineRepository.count(r.getId()));
			r.setTags(articleRelateTagRepository.findAllByArticle(r.getId()).stream()
					.map(art -> tagRepository.find(art.getTagId()))
					.collect(Collectors.toList()));
		}).collect(Collectors.toList());

		List<PopularArticle> popularArticles = popularArticlesRepository.findAll().stream().peek(pa -> {
			pa.setArticle(articlesRepository.find(pa.getArticleId()));
			pa.setIineUsers(iineRepository.findAll(pa.getArticleId()));
		}).collect(Collectors.toList());

		List<Integer> onlineUsers = loginUserRepository.findAll();

		model.addAttribute("current", "new");
		model.addAttribute("path", "");
		model.addAttribute("page", page);
		model.addAttribute("maxPage", maxPage);
		model.addAttribute("from", page <= 5 ? 1 : page - 5);
		model.addAttribute("to", maxPage - page < 5 ? maxPage : page + 5);

		model.addAttribute("user", user);
		model.addAttribute("articles", articles);
		model.addAttribute("popularArticles", popularArticles);
		model.addAttribute("onlineUsers", onlineUsers);

		return "index";
	}

	@RequestMapping(value = "login", method = RequestMethod.GET)
	String getLogin(Model model) {
		model.addAttribute("message", "SGE内で培われた技術や知見を横軸全体で共有するサービス");
		return "login";
	}

	@RequestMapping(value = "login", method = RequestMethod.POST)
	String postLogin(@RequestParam("email") String email, @RequestParam("password") String password) {
		authenticate(email, password);
		request.changeSessionId();

		return "redirect:/";
	}

	@RequestMapping(value = "/regist", method = RequestMethod.GET)
	String getRegist(Model model) {
		model.addAttribute("message", "新規登録して利用を開始しましょう。");
		return "regist";
	}

	@RequestMapping(value = "/regist", method = RequestMethod.POST)
	@Transactional
	String postRegist(Model model, @RequestParam("username") String userName, @RequestParam("email") String email, @RequestParam("password") String password) {
		if (StringUtils.isEmpty(userName) || StringUtils.isEmpty(email) || StringUtils.isEmpty(password)) {
			model.addAttribute("message", "全て必須入力です。");
			return "regist";
		}

		String salt = this.generateSalt(6);
		Integer userId = userRepository.register(userName, email, password, salt);
		if (userId == null) {
			model.addAttribute("message", "既に登録済みです。");
			return "regist";
		}
		saltRepository.register(userId, salt);
		loginUserRepository.set(userId);

		session.setAttribute("user_id", userId);
		return "redirect:/";
	}

	@RequestMapping(value = "logout", method = RequestMethod.GET)
	String getLogout(Model model) {
		try {
			session.invalidate();
		} catch (IllegalStateException ignored) {
		}

		return "redirect:/login";
	}

	@RequestMapping(value = "/photo/{userId}", method = RequestMethod.GET)
	@ResponseBody
	byte[] getPhoto(Model model, @PathVariable("userId") Integer userId, HttpServletResponse res) throws IOException {

		UserPhoto userPhoto = userPhotoRepository.find(userId);
		byte[] body = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAIAAAACACAYAAADDPmHLAAAA3klEQVR42u3SAQ0AAAgDIN8/9K3hJmQgnXZ4KwIIIIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACCCAAAIIIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAA3LNkJfxBbCdD2AAAAAElFTkSuQmCC");
		res.setHeader("Content-Type", "image/png");

		if (userPhoto == null) {
			return body;
		}
		if (userPhoto.getPhotoBinary() != null && userPhoto.getPhotoBinary().length > 0) {
			return userPhoto.getPhotoBinary();
		}
		Resource resource = resourceLoader.getResource("classpath:" + "/static/photo/" + userPhoto.getPhotoPath());
		if (resource == null || !resource.getFile().exists()) {
			return body;
		}
		return IOUtils.toByteArray(resource.getInputStream());
	}

	@RequestMapping(value = "/article/{articleId}", method = RequestMethod.GET)
	String getArticle(Model model, @PathVariable("articleId") Integer articleId, HttpServletResponse res) {
		if (!isAuthenticated()) {
			return "redirect:/login";
		}
		User user = currentUser();
		Integer iineCount = iineRepository.count(articleId, user.getId());
		Article article = articlesRepository.find(articleId);
		if (article == null) {
			res.setStatus(HttpStatus.NOT_FOUND.value());
			return "error";
		}

		article.setUser(userRepository.find(article.getAuthorId()));
		article.setDeltaTime(deltaTime(article.getUpdatedAt()));

		model.addAttribute("user", userRepository.find(currentUser().getId()));
		model.addAttribute("article", article);
		model.addAttribute("tags", articleRelateTagRepository.findAllByArticle(articleId).stream()
				.map(art -> tagRepository.find(art.getTagId()))
				.collect(Collectors.toList()));
		model.addAttribute("iineCount", iineRepository.count(articleId));
		model.addAttribute("iineUsers", iineRepository.findAll(articleId));
		model.addAttribute("doneIine", iineCount);

		return "article";
	}

	@ResponseBody @RequestMapping(path = "/iine/{articleId}", method = RequestMethod.POST)
	String postIine(@PathVariable("articleId") Integer articleId, @RequestParam("name") String name, HttpServletResponse res) {
		if (!isAuthenticated()) {
			res.setStatus(HttpStatus.UNAUTHORIZED.value());
			return "";
		}
		User user = currentUser();

		if (name.equals("plus")) {
			iineRepository.register(articleId, user.getId());
		} else {
			iineRepository.delete(articleId, user.getId());
		}
		return String.valueOf(iineRepository.count(articleId));
	}

	@RequestMapping(value = "/tags", method = RequestMethod.GET)
	String getTags(Model model, @RequestParam(name = "page", required = false) Integer page, HttpServletResponse res) {
		if (!isAuthenticated()) {
			return "redirect:/login";
		}
		if (page == null) {
			page = 1;
		}
		Integer tagCount = tagRepository.count();
		int pageSize = 20;
		int maxPage = (int) Math.ceil((double)tagCount / (double)pageSize);
		if (maxPage == 0) {
			maxPage = 1;
		}
		if (page < 1 || maxPage < page) {
			res.setStatus(HttpStatus.BAD_REQUEST.value());
			return "error";
		}
		int offset = (page - 1) * pageSize;

		User user = userRepository.find(currentUser().getId());

		List<Tag> tags = tagRepository.findAll(offset, pageSize).stream().peek(r -> {
			r.setTagCount(articleRelateTagRepository.count(r.getId()));
		}).collect(Collectors.toList());

		model.addAttribute("page", page);
		model.addAttribute("maxPage", maxPage);
		model.addAttribute("from", page <= 5 ? 1 : page - 5);
		model.addAttribute("to", maxPage - page < 5 ? maxPage : page + 5);

		model.addAttribute("user", user);
		model.addAttribute("tags", tags);
		model.addAttribute("tagCount", tagCount);

		return "tags";
	}

	@RequestMapping(value = "/tag/{tagId}", method = RequestMethod.GET)
	String getTag(
			Model model, 
			@PathVariable("tagId") Integer tagId,
			@RequestParam(name = "page", required = false)
			Integer page, HttpServletResponse res
	) {
		if (!isAuthenticated()) {
			return "redirect:/login";
		}
		if (page == null) {
			page = 1;
		}
		Integer tagCount = articleRelateTagRepository.count(tagId);
		int pageSize = 20;
		int maxPage = (int) Math.ceil((double)tagCount / (double)pageSize);
		if (maxPage == 0) {
			maxPage = 1;
		}
		if (page < 1 || maxPage < page) {
			res.setStatus(HttpStatus.BAD_REQUEST.value());
			return "error";
		}
		int offset = (page - 1) * pageSize;

		User user = userRepository.find(currentUser().getId());

		List<ArticleRelateTag> arts = articleRelateTagRepository.findAllByTag(tagId, offset, pageSize).stream().peek(r -> {
			Article article = articlesRepository.find(r.getArticleId());
			r.setArticle(article);
			r.setAuthor(userRepository.find(article.getAuthorId()));
			r.setIineCount(iineRepository.count(r.getArticleId()));
			r.setDeltaTime(this.deltaTime(article.getUpdatedAt()));
			r.setTags(articleRelateTagRepository.findAllByArticle(r.getArticleId()).stream()
					.map(art -> tagRepository.find(art.getTagId()))
					.collect(Collectors.toList()));
		}).collect(Collectors.toList());

		if (CollectionUtils.isEmpty(arts)) {
			res.setStatus(HttpStatus.NOT_FOUND.value());
			return "error";
		}

		model.addAttribute("page", page);
		model.addAttribute("maxPage", maxPage);
		model.addAttribute("from", page <= 5 ? 1 : page - 5);
		model.addAttribute("to", maxPage - page < 5 ? maxPage : page + 5);

		model.addAttribute("user", user);
		model.addAttribute("tagId", tagId);
		model.addAttribute("articles", arts);

		return "tag";
	}

	@RequestMapping(value = "/members", method = RequestMethod.GET)
	String getMembers(Model model, @RequestParam(name = "page", required = false) Integer page, HttpServletResponse res) {
		if (!isAuthenticated()) {
			return "redirect:/login";
		}
		if (page == null) {
			page = 1;
		}
		Integer userCount = userRepository.count();
		int pageSize = 20;
		int maxPage = (int) Math.ceil((double)userCount / (double)pageSize);
		if (maxPage == 0) {
			maxPage = 1;
		}
		if (page < 1 || maxPage < page) {
			res.setStatus(HttpStatus.BAD_REQUEST.value());
			return "error";
		}
		int offset = (page - 1) * pageSize;

		User user = userRepository.find(currentUser().getId());

		List<User> users = userRepository.findAll(offset, pageSize).stream().peek(r -> {
			r.setArticleCount(articlesRepository.count(r.getId()));
		}).collect(Collectors.toList());

		model.addAttribute("page", page);
		model.addAttribute("maxPage", maxPage);
		model.addAttribute("from", page <= 5 ? 1 : page - 5);
		model.addAttribute("to", maxPage - page < 5 ? maxPage : page + 5);

		model.addAttribute("user", user);
		model.addAttribute("members", users);
		model.addAttribute("memberCount", userCount);

		return "members";
	}

	@RequestMapping(value = "/member/{memberId}", method = RequestMethod.GET)
	String getMember(
			Model model, 
			@PathVariable("memberId") Integer memberId,
			@RequestParam(name = "page", required = false)
			Integer page, HttpServletResponse res
	) {
		if (!isAuthenticated()) {
			return "redirect:/login";
		}
		if (page == null) {
			page = 1;
		}
		Integer articleCount = articlesRepository.count(memberId);
		int pageSize = 20;
		int maxPage = (int) Math.ceil((double)articleCount / (double)pageSize);
		if (maxPage == 0) {
			maxPage = 1;
		}
		if (page < 1 || maxPage < page) {
			res.setStatus(HttpStatus.BAD_REQUEST.value());
			return "error";
		}
		int offset = (page - 1) * pageSize;

		User member = userRepository.find(memberId);
		if (member == null) {
			res.setStatus(HttpStatus.NOT_FOUND.value());
			return "error";
		}

		User user = userRepository.find(currentUser().getId());
		List<Article> articles = articlesRepository.findAll(memberId, offset, pageSize).stream().peek(r -> {
			r.setUser(userRepository.find(r.getAuthorId()));
			r.setIineCount(iineRepository.count(r.getId()));
			r.setDeltaTime(this.deltaTime(r.getUpdatedAt()));
			r.setTags(articleRelateTagRepository.findAllByArticle(r.getId()).stream()
					.map(art -> tagRepository.find(art.getTagId()))
					.collect(Collectors.toList()));
		}).collect(Collectors.toList());

		model.addAttribute("page", page);
		model.addAttribute("maxPage", maxPage);
		model.addAttribute("from", page <= 5 ? 1 : page - 5);
		model.addAttribute("to", maxPage - page < 5 ? maxPage : page + 5);

		model.addAttribute("user", user);
		model.addAttribute("author", userRepository.find(memberId));
		model.addAttribute("articles", articles);
		model.addAttribute("articleCount", articleCount);

		return "member";
	}

	@RequestMapping(value = "/write", method = RequestMethod.GET)
	String getWrite(Model model) {
		if (!isAuthenticated()) {
			return "redirect:/login";
		}

		model.addAttribute("user", userRepository.find(currentUser().getId()));
		model.addAttribute("message", "Jiriqiのあがる記事を書こう！");

		return "write";
	}

	@RequestMapping(value = "/write", method = RequestMethod.POST)
	@Transactional
	String postWrite(Model model, 
			@RequestParam("title") String title,
			@RequestParam("tags") String tags,
			@RequestParam("articleBody") String articleBody
	) {
		if (!isAuthenticated()) {
			return "redirect:/login";
		}
		User user = currentUser();
		Integer articleId = articlesRepository.register(user.getId(), title, articleBody);

		Stream.of(tags.split(",")).map(r -> r.trim()).map(r -> {
			Tag tag = tagRepository.find(r);
			if (tag == null) {
				return tagRepository.register(r);
			} else {
				return tag.getId();
			}
		}).forEach(r -> {
			articleRelateTagRepository.register(articleId, r);
		});

		return "redirect:/article/" + articleId;
	}
	
	@RequestMapping(value = "/update/{articleId}", method = RequestMethod.GET)
	String getUpdate(Model model, @PathVariable("articleId") Integer articleId, HttpServletResponse res) {
		if (!isAuthenticated()) {
			return "redirect:/login";
		}
		User user = currentUser();

		Article article = articlesRepository.find(articleId);
		if (user == null || article == null || !user.getId().equals(article.getAuthorId())) {
			res.setStatus(HttpStatus.FORBIDDEN.value());
			return "error";
		}
		String tags = articleRelateTagRepository.findAllByArticle(articleId).stream()
				.map(r -> tagRepository.find(r.getTagId()))
				.map(r -> r.getTagname())
				.collect(Collectors.joining(","));

		model.addAttribute("user", userRepository.find(currentUser().getId()));
		model.addAttribute("title", article.getTitle());
		model.addAttribute("tags", tags);
		model.addAttribute("articleBody", article.getDescription());
		model.addAttribute("articleId", articleId);
		model.addAttribute("message", "Jiriqiのあがる記事の更新");

		return "update";
	}

	@RequestMapping(value = "/update/{articleId}", method = RequestMethod.POST)
	@Transactional
	String postUpdate(Model model, 
			@PathVariable("articleId") Integer articleId,
			@RequestParam("title") String title,
			@RequestParam("tags") String tags,
			@RequestParam("articleBody") String articleBody
	) {
		if (!isAuthenticated()) {
			return "redirect:/login";
		}
		articlesRepository.update(articleId, title, articleBody);

		articleRelateTagRepository.delete(articleId);
		Stream.of(tags.split(",")).map(r -> r.trim()).map(r -> {
			Tag tag = tagRepository.find(r);
			if (tag == null) {
				return tagRepository.register(r);
			} else {
				return tag.getId();
			}
		}).forEach(r -> {
			articleRelateTagRepository.register(articleId, r);
		});

		return "redirect:/article/" + articleId;
	}

	@RequestMapping(value = "/profileupdate/{userId}", method = RequestMethod.GET)
	String getProfileUpdate(Model model, @PathVariable("userId") Integer userId, HttpServletResponse res) {
		if (!isAuthenticated()) {
			return "redirect:/login";
		}
		User user = currentUser();
		if (!user.getId().equals(userId)) {
			res.setStatus(HttpStatus.FORBIDDEN.value());
			return "error";
		}

		model.addAttribute("user", currentUser());
		model.addAttribute("message", "プロフィールを変更します");

		return "profileupdate";
	}

	@RequestMapping(value = "/profileupdate/{userId}", method = RequestMethod.POST)
	String postProfileUpdate(
			Model model, 
			@PathVariable("userId") Integer userId,
            @RequestParam("icon_file") MultipartFile file,
            @RequestParam("nick_name") String nickName,
            @RequestParam("email") String email,
            @RequestParam("current_password") String currentPassword,
            @RequestParam("new_password") String newPassword,
			HttpServletResponse res
	) throws IOException {
		if (!isAuthenticated()) {
			return "redirect:/login";
		}
		User currentUser = currentUser();
		if (!currentUser.getId().equals(userId)) {
			res.setStatus(HttpStatus.FORBIDDEN.value());
			return "error";
		}

		if (file != null && file.getSize() != 0) {
			userPhotoRepository.save(userId, file.getBytes());
		}

		if (!StringUtils.isEmpty(newPassword)) {
			Salt salt = saltRepository.find(userId);
			User user = userRepository.find(userId, currentPassword, salt.getSalt());
			if (user == null) {
				model.addAttribute("user", currentUser());
				model.addAttribute("message", "現在のパスワードが違います。");
				return "profileupdate";
			}

			String newSalt = this.generateSalt(6);
			userRepository.updatePassword(userId, newPassword, newSalt);
			saltRepository.update(userId, newSalt);
		}
		if (!StringUtils.isEmpty(nickName)) {
			userRepository.updateNickname(userId, nickName);
		}
		if (!StringUtils.isEmpty(email)) {
			userRepository.updateEmail(userId, email);
		}

		session.setAttribute("user", userRepository.find(userId));
		return "redirect:/member/" + userId;
	}

	@RequestMapping(value = "/initialize", method = RequestMethod.GET)
	public ResponseEntity<String> initialize() {
		initializer.initialize();
		return ResponseEntity.ok("");
	}

	private void authenticate(String email, String password) {
		User user = userRepository.findByEmailAndRawPassword(email, password);
		if (user == null) {
			throw new AuthenticationError();
		}
		loginUserRepository.set(user.getId());

		session.setAttribute("user_id", user.getId());
	}

	private User currentUser() {
		Object user = session.getAttribute("user");
		if (user != null) {
			return User.class.cast(user);
		}

		Object userId = session.getAttribute("user_id");
		if (userId == null) {
			return null;
		}

		User currentUser = getUser(Integer.class.cast(userId));
		if (currentUser == null) {
			throw new AuthenticationError();
		}

		session.setAttribute("user", currentUser);
		return currentUser;
	}

	User getUser(Integer userId) {
		User user = userRepository.find(userId);
		if (user == null) {
			throw new ContentNotFound();
		}

		return user;
	}

	boolean isAuthenticated() {
		return currentUser() != null;
	}

	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	@ExceptionHandler(AuthenticationError.class)
	ModelAndView authenticationError() {
		return new ModelAndView("login").addObject("message", "ログインに失敗しました");
	}

	@ResponseStatus(HttpStatus.FORBIDDEN)
	@ExceptionHandler(PermissionDenied.class)
	ModelAndView permissionDenied() {
		return new ModelAndView("error").addObject("message", "友人のみしかアクセスできません");
	}

	@ResponseStatus(HttpStatus.NOT_FOUND)
	@ExceptionHandler(ContentNotFound.class)
	ModelAndView contentNotFound() {
		return new ModelAndView("error").addObject("message", "要求されたコンテンツは存在しません");
	}

	public static class AuthenticationError extends RuntimeException {
	}

	public static class PermissionDenied extends RuntimeException {
	}

	public static class ContentNotFound extends RuntimeException {
	}

	@Autowired
	SaltRepository saltRepository;
	@Autowired
	UserRepository userRepository;
	@Autowired
	ArticleRelateTagRepository articleRelateTagRepository;
	@Autowired
	PopularArticlesRepository popularArticlesRepository;
	@Autowired
	TagRepository tagRepository;
	@Autowired
	IineRepository iineRepository;
	@Autowired
	LoginUserRepository loginUserRepository;
	@Autowired
	UserPhotoRepository userPhotoRepository;
	@Autowired
	HitmeInitializer initializer;

	@Autowired
	HttpSession session;
	@Autowired
	HttpServletRequest request;

	@Bean
	ServletContextInitializer sevletContextInitializer() {
		return servletContext -> servletContext
				.setSessionTrackingModes(Collections.singleton(SessionTrackingMode.COOKIE));
	}

	@Repository
	public static class HitmeInitializer {
		@Autowired
		JdbcTemplate jdbcTemplate;
		@Autowired
		LoginUserRepository loginUserRepository;

		@Transactional
		public void initialize() {
			jdbcTemplate.update("DELETE FROM users WHERE id > 5000");
			jdbcTemplate.update("DELETE FROM salts WHERE user_id > 5000");
			jdbcTemplate.update("DELETE FROM user_photos WHERE user_id > 5000");
			jdbcTemplate.update("DELETE FROM tags WHERE id > 999");
			jdbcTemplate.update("DELETE FROM iines WHERE id > 50000");
			jdbcTemplate.update("DELETE FROM articles WHERE id > 7101");
			jdbcTemplate.update("DELETE FROM article_relate_tags WHERE article_id > 7101");

			loginUserRepository.flushAll();
			IntStream.rangeClosed(1, 500).forEach(r -> loginUserRepository.set(r));
		}
	}

	private String generateSalt(int length) {
		String str = "";
		while (length-- > 0) {
			List<String> list = Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "a", "b", "c", "d", "e", "f");
			Collections.shuffle(list);
			str += list.get(0);
		}
		return str;
	}

	private String deltaTime(Date date) {
		long deltaTime = new Date().getTime() - date.getTime();
		if (deltaTime / (60*60*24*30*12) > 0) {
			return deltaTime / (60*60*24*30*12) + "年前";
		} else if (deltaTime / (60*60*24*30) > 0) {
			return deltaTime / (60*60*24*30) + "ヶ月前";
		} else if (deltaTime / (60*60*24) > 0) {
			return deltaTime / (60*60*24) + "日前";
		} else if (deltaTime / (60*60) > 0) {
			return deltaTime / (60*60) + "時間前";
		} else if (deltaTime / 60 > 0) {
			return deltaTime / 60 + "分前";
		} else {
			return "たったいま";
		}
	}

	public static void main(String[] args) {
		SpringApplication.run(App.class, args);
	}
}
