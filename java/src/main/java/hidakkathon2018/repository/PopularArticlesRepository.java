package hidakkathon2018.repository;

import java.util.*;

import org.springframework.beans.factory.annotation.*;
import org.springframework.dao.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.*;

import hidakkathon2018.model.*;

@Repository
public class PopularArticlesRepository {
	@Autowired
	NamedParameterJdbcTemplate jdbcTemplate;

	RowMapper<PopularArticle> rowMapper = (rs, i) -> {
		PopularArticle article = new PopularArticle();
		article.setArticleId(rs.getInt("article_id"));
		article.setIineCount(rs.getInt("iineCount"));
		return article;
	};

	public List<PopularArticle> findAll() {
		try {
			return jdbcTemplate.query(
					"SELECT article_id, COUNT(user_id) AS iineCount FROM iines WHERE updated_at >= DATE_ADD(NOW(), INTERVAL -1 MONTH) GROUP BY article_id ORDER BY iineCount DESC, article_id DESC LIMIT 5",
					new MapSqlParameterSource(), rowMapper);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}
}
