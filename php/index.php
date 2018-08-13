<?php
require 'vendor/autoload.php';

use Slim\Http\Request;
use Slim\Http\Response;
use Slim\Http\UploadedFile;

use Dflydev\FigCookies\FigResponseCookies;
use Dflydev\FigCookies\FigRequestCookies;
use Dflydev\FigCookies\Cookie;
use Dflydev\FigCookies\SetCookie;

date_default_timezone_set('Asia/Tokyo');

define("TWIG_TEMPLATE_FOLDER", realpath(__DIR__) . "/views");

function getPDO()
{
    static $pdo = null;
    if (!is_null($pdo)) {
        return $pdo;
    }
    $host = getenv('HIDAKKATHON_DB_HOST') ?: 'localhost';
    $port = getenv('HIDAKKATHON_DB_PORT') ?: '3306';
    $user = getenv('HIDAKKATHON_DB_USER') ?: 'ubuntu';
    $password = getenv('ISUBATA_DB_PASSWORD') ?: 'ubuntu';
    $dsn = "mysql:host={$host};port={$port};dbname=hidakkathon;charset=utf8mb4";
    $pdo = new PDO(
        $dsn,
        $user,
        $password,
        [
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION
        ]
    );
    $pdo->query("SET SESSION sql_mode='TRADITIONAL,NO_AUTO_VALUE_ON_ZERO,ONLY_FULL_GROUP_BY'");
    return $pdo;
}

function getRedis()
{
    $redis = new Redis();
    $redis->connect('127.0.0.1', 6379);

    return $redis;
}



/*
for($i=1;$i<=5000;$i++) {
    $redis = getRedis();
    $redis->set($i . '_p', $i.'.png');
}

print('OK');
*/


$app = new \Slim\App();
$container = $app->getContainer();
$container['view'] = function ($container) {
    $view = new \Slim\Views\Twig(TWIG_TEMPLATE_FOLDER, []);
    $view->addExtension(
        new \Slim\Views\TwigExtension(
            $container['router'],
            $container['request']->getUri()
        )
    );
    return $view;
};

function getNow()
{
    return date('Y/m/d H:i:s', $_SERVER['REQUEST_TIME']);
}

function dbGetUser($dbh, $userId)
{
    $stmt = $dbh->prepare("SELECT * FROM users WHERE id = ?");
    $stmt->execute([$userId]);
    return $stmt->fetch();
}

$loginRequired = function (Request $request, Response $response, $next) use ($container) {
    $userId = FigRequestCookies::get($request, 'user_id')->getValue();
    if (!$userId) {
        return $response->withRedirect('/login', 303);
    }
    setLogin($userId);

    $request = $request->withAttribute('user_id', $userId);
    $container['view']->offsetSet('user_id', $userId);
    $user = dbGetUser(getPDO(), $userId);
    if (!$user) {
        $response = FigResponseCookies::remove($response, 'user_id');
        return $response->withRedirect('/login', 303);
    }
    $request = $request->withAttribute('user', $user);
    $container['view']->offsetSet('user', $user);
    $response = $next($request, $response);
    return $response;
};


function abortAuthenticationError(Response $response)
{
    $response = FigResponseCookies::remove($response, 'user_id');
    $this->view->render($response, 'login.twig', ['message' => 'ログインに失敗しました'], 401);
    $app->stop();
}


function dbExecute($query, $args = [])
{
    $stmt = getPDO()->prepare($query);
    $stmt->execute($args);
    return $stmt;
}

function authenticate($email, $password)
{
    $query = <<<__SQL
        SELECT u.id AS id, u.nick_name AS nick_name, u.email AS email
        FROM users u
        JOIN salts s ON u.id = s.user_id
        WHERE u.email = ? AND u.passhash = SHA2(CONCAT(?, s.salt), 512)
__SQL;
    $result = dbExecute($query, [$email, $password])->fetch();
    if (!$result) {
        return false;
    }
    return $result;
}

function currentUser(Request $request, Response $response)
{
    $userId = FigRequestCookies::get($request, 'user_id')->getValue();
    $user = dbExecute('SELECT id,  nick_name, email FROM users WHERE id=?', [$userId])->fetch();
    if (!$user) {
        abortAuthenticationError($response);
    }
    return $user;
}



function setLogin($userId)
{
    $redis = getRedis();
    $redis->set('login_' . $userId, time());
}

function getLoginUsers()
{
    $onLineUsers = [];
    $redis = getRedis();
    $loginUsers = $redis->keys('login_*');
    foreach ($loginUsers as $key => $value) {
        $loginTime = $redis->get($value);
        if (time() - $loginTime < 60*60) {
            $onLineUsers[] = substr($value, 6);
        }
    }
    sort($onLineUsers, SORT_NUMERIC);

    return $onLineUsers;
}


function randomString($length)
{
    $str = "";
    while ($length--) {
        $str .= str_shuffle("1234567890abcdef")[0];
    }
    return $str;
}

function register($dbh, $userName, $email, $password)
{
    $salt = randomString(6);
    $stmt = $dbh->prepare(
        "INSERT INTO users (nick_name, email, passhash) ".
        "VALUES (?, ?, SHA2(CONCAT(?, ?), 512))"
    );
    $stmt->execute([$userName, $email, $password, $salt]);
    $stmt = $dbh->query("SELECT LAST_INSERT_ID() AS last_insert_id");
    $userId = $stmt->fetch()['last_insert_id'];
    $stmt = $dbh->prepare(
        "INSERT INTO salts (user_id, salt) VALUES ( ?, ? )"
    );
    $stmt->execute([$userId, $salt]);
    return $userId;
}


function deltaTime(String $updatetime)
{
    $deltaString = "";
    $deltaInt = time() - strtotime($updatetime);
    if (intdiv($deltaInt, 60*60*24*30*12) > 0) {
        $deltaString = intdiv($deltaInt, 60*60*24*30*12) . "年前";
    } elseif (intdiv($deltaInt, 60*60*24*30) > 0) {
        $deltaString = intdiv($deltaInt, 60*60*24*30) . "ヶ月前";
    } elseif (intdiv($deltaInt, 60*60*24) > 0) {
        $deltaString = intdiv($deltaInt, 60*60*24) . "日前";
    } elseif (intdiv($deltaInt, 60*60) > 0) {
        $deltaString = intdiv($deltaInt, 60*60) . "時間前";
    } elseif (intdiv($deltaInt, 60) > 0) {
        $deltaString = intdiv($deltaInt, 60) . "分前" ;
    } else {
        $deltaString = "たったいま";
    }
    return $deltaString;
}

function getIineCount($articleId)
{
    $query = "SELECT COUNT(`id`) as cnt FROM iines WHERE article_id = ? ";
    $cnt = dbExecute($query, [$articleId])->fetch()['cnt'];
    return $cnt;
}

function getTagCount($tagId)
{
    $query = "SELECT COUNT(*) as cnt FROM article_relate_tags WHERE tag_id = ? ";
    $cnt = dbExecute($query, [$tagId])->fetch()['cnt'];
    return $cnt;
}

function getArticleCount($authorId)
{
    $query = "SELECT COUNT(*) as cnt FROM articles WHERE author_id = ? ";
    $cnt = dbExecute($query, [$authorId])->fetch()['cnt'];
    return $cnt;
}

function getArticleTagnames($articleId)
{
    $tagNames=[];
    $query = "SELECT tag_id FROM article_relate_tags WHERE article_id = ? ORDER BY tag_id ASC";
    $tagIds = dbExecute($query, [$articleId])->fetchAll();
    foreach ($tagIds as $tagId) {
        $query = "SELECT tagname FROM tags WHERE id = ?";
        $ret = dbExecute($query, [$tagId['tag_id']])->fetch()['tagname'];
        if ($ret) {
            $tagNames[] = ['tagId' => $tagId['tag_id'], 'name' => $ret];
        }
    }
    return $tagNames;
}

function getArticle($id)
{
    $query = "SELECT * FROM articles WHERE id = ? ";
    $article = dbExecute($query, [$id])->fetch();
    return $article;
}

function getArticleIineUsers($id)
{
    $query = "SELECT user_id FROM iines WHERE article_id = ? ";
    $iineUsers = dbExecute($query, [$id])->fetchAll();
    return $iineUsers;
}

function getPopularArticles()
{
    $query = <<<__SQL
	SELECT
	  article_id,
	  count(user_id) as iineCnt
	FROM
	  iines
	WHERE
	  updated_at >= DATE_ADD(NOW(), INTERVAL -1 MONTH)
	GROUP BY
	  article_id
	ORDER BY
      iineCnt DESC,
      article_id DESC
	LIMIT 5
__SQL;
    $popularArticles = dbExecute($query)->fetchAll();

    foreach ($popularArticles as $key => $article) {
        $popularArticles[$key]['article'] = getArticle($article['article_id']);
        $popularArticles[$key]['iine_users'] = getArticleIineUsers($article['article_id']);
    }

    return $popularArticles;
}


function InsArticle($dbh, $userId, $title, $tags, $articleBody)
{
    $query = "INSERT INTO articles ( author_id, title, description, updated_at, created_at ) VALUES ( ?, ?, ?, ?, ?);";
    $stmt = $dbh->prepare($query);
    $stmt->execute([$userId, $title, $articleBody, getNow(), getNow()]);

    $stmt = $dbh->query("SELECT LAST_INSERT_ID() AS last_insert_id");
    $articleId = $stmt->fetch()['last_insert_id'];


    if ($tags) {
        $tagArray = explode(',', $tags);

        $articleTagIds = [];
        foreach ($tagArray as $tag) {
            $stmt = $dbh->prepare("SELECT id FROM tags WHERE tagname = ?");
            $stmt->execute([trim($tag)]);
            $tagId = $stmt->fetch()['id'];
            if ($tagId) {
                $articleTagIds[] = $tagId;
            } else {
                $stmt = $dbh->prepare("INSERT INTO tags (tagname) VALUES ( ? );");
                $stmt->execute([trim($tag)]);
                $stmt = $dbh->query("SELECT LAST_INSERT_ID() AS last_insert_id");
                $articleTagIds[] = $stmt->fetch()['last_insert_id'];
            }
        }

        foreach ($articleTagIds as $articleTagId) {
            $stmt = $dbh->prepare("INSERT INTO article_relate_tags (article_id, tag_id) VALUES ( ?, ? );");
            $stmt->execute([$articleId, $articleTagId]);
        }
    }
    return $articleId;
}

function UpdArticle($dbh, $userId, $articleId, $title, $tags, $articleBody)
{
    $query = "UPDATE articles set title = ? , description = ?, updated_at = ? WHERE id = ?";

    $stmt = $dbh->prepare($query);
    $stmt->execute([$title, $articleBody, getNow(), $articleId]);

    if ($tags) {
        $tagArray = explode(',', $tags);

        $articleTagIds = [];
        foreach ($tagArray as $tag) {
            $stmt = $dbh->prepare("SELECT id FROM tags WHERE tagname = ?");
            $stmt->execute([trim($tag)]);
            $tagId = $stmt->fetch()['id'];
            if ($tagId) {
                $articleTagIds[] = $tagId;
            } else {
                $stmt = $dbh->prepare("INSERT INTO tags (tagname) VALUES ( ? );");
                $stmt->execute([trim($tag)]);
                $stmt = $dbh->query("SELECT LAST_INSERT_ID() AS last_insert_id");
                $articleTagIds[] = $stmt->fetch()['last_insert_id'];
            }
        }

        $stmt = $dbh->prepare("DELETE FROM article_relate_tags  WHERE article_id = ?");
        $stmt->execute([$articleId]);

        foreach ($articleTagIds as $articleTagId) {
            $stmt = $dbh->prepare("INSERT INTO article_relate_tags (article_id, tag_id) VALUES ( ?, ? );");
            $stmt->execute([$articleId, $articleTagId]);
        }
    }
}
function insAndUpdUserPhoto($dbh, $userId, $photoPath, $photoBinary)
{
    if ($photoBinary) {
        $query =<<<__SQL
            INSERT INTO 
              user_photos 
              (
                user_id, 
                photo_binary, 
                updated_at
              ) 
            VALUES
              ( ?, ?, ? )
            ON DUPLICATE KEY
            UPDATE
              photo_binary = ?,
              updated_at = ?;
__SQL;
        $stmt = $dbh->prepare($query);
        $stmt->execute([$userId, $photoBinary, getNow(), $photoBinary, getNow()]);
    } elseif ($photoPath) {
        $query =<<<__SQL
            INSERT INTO 
              user_photos 
              (
                user_id, 
                photo_path, 
                updated_at
              ) 
            VALUES
              ( ?, ?, ? )
            ON DUPLICATE KEY
            UPDATE
              photo_path = ?,
              updated_at = ?;
__SQL;
        $stmt = $dbh->prepare($query);
        $stmt->execute([$userId, $photoPath, getNow(), $photoPath, getNow()]);
    }
}

function updatePassword($dbh, $userId, $currentPassword, $newPassword)
{
    $query = <<<__SQL
        SELECT
          COUNT(u.id) AS cnt
        FROM 
          users u
        JOIN 
          salts s ON u.id = s.user_id
        WHERE 
          u.id = ? 
        AND 
          u.passhash = SHA2(CONCAT(?, s.salt), 512);
__SQL;
    $stmt = $dbh->prepare($query);
    $stmt->execute([$userId, $currentPassword]);
    $cnt = (int)$stmt->fetch()['cnt'];

    if ($cnt !== 1) {
        return "現在のパスワードが違います";
    }
    $salt = randomString(6);
    $query = "UPDATE users SET passhash = SHA2(CONCAT(?, ?), 512) WHERE id = ?";
    $stmt  = $dbh->prepare($query);
    $stmt->execute([$newPassword, $salt, $userId]);

    $query = "UPDATE salts SET salt =  ? WHERE user_id = ?";
    $stmt  = $dbh->prepare($query);
    $stmt->execute([$salt, $userId]);

    return "";
}
function updateNickName($dbh, $userId, $nickName)
{
    $query = "UPDATE users SET nick_name = ? WHERE id = ?";
    $stmt = $dbh->prepare($query);
    $stmt->execute([$nickName, $userId]);
}

function updateEmail($dbh, $userId, $email)
{
    $query = "UPDATE users SET email = ? WHERE id = ?";
    $stmt = $dbh->prepare($query);
    $stmt->execute([$email, $userId]);
}

function getUploadedFileBinary(UploadedFile $uploadedFile)
{
    $stream = $uploadedFile->getStream();
    
    return $stream->getContents();
}

$app->get('/login', function (Request $request, Response $response) {
    $this->view->render($response, 'login.twig', ['message' => 'SGE内で培われた技術や知見を横軸全体で共有するサービス']);
});

$app->post('/login', function (Request $request, Response $response) {
    $email = $request->getParam('email');
    $password = $request->getParam('password');
    $ret = authenticate($email, $password);
    if (!$ret) {
        $response = FigResponseCookies::remove($response, 'user_id');
        return $this->view->render($response, 'login.twig', ['message' => 'ログインに失敗しました']);
    }
    $response = FigResponseCookies::set($response, SetCookie::create('user_id', $ret['id']));
    setLogin($ret['id']);
    return $response->withRedirect('/', 303);
});

$app->get('/regist', function (Request $request, Response $response) {
    $this->view->render($response, 'regist.twig', ['message' => '新規登録して利用を開始しましょう。']);
});

$app->post('/regist', function (Request $request, Response $response) {
    $name     = $request->getParam('username');
    $email    = $request->getParam('email');
    $password = $request->getParam('password');
    if (!$name || !$password || !$name) {
        return $this->view->render($response, 'regist.twig', ['message' => '全て必須入力です']);
    }
    $dbh = getPDO();
    $dbh->beginTransaction();
    try {
        $userId = register($dbh, $name, $email, $password);
    } catch (PDOException $e) {
        $dbh->rollBack();
        if ($e->errorInfo[1] === 1062) {
            return $this->view->render($response, 'regist.twig', ['message' => 'すでに登録済みです']);
        }
        throw $e;
    }
    $dbh->commit();
    $response = FigResponseCookies::set($response, SetCookie::create('user_id', $userId));
    return $response->withRedirect('/', 303);
});

$app->get('/logout', function (Request $request, Response $response) {
    $response = FigResponseCookies::set($response, SetCookie::create('user_id', '0'));
    return $response->withRedirect('/', 303);
});

$app->get('/photo/{member_id}', function (Request $request, Response $response) {
    $memberId = $request->getAttribute('member_id');

    $memberId = $request->getAttribute('member_id');
    $user_photo = dbExecute('SELECT * FROM user_photos WHERE user_id = ?', [$memberId])->fetch();

    $body = "";

    if (!$user_photo) {
        $body = base64_decode("iVBORw0KGgoAAAANSUhEUgAAAIAAAACACAYAAADDPmHLAAAA3klEQVR42u3SAQ0AAAgDIN8/9K3hJmQgnXZ4KwIIIIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACCCAAAIIIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAA3LNkJfxBbCdD2AAAAAElFTkSuQmCC");
    } elseif ($user_photo['photo_binary']) {
        $body = $user_photo['photo_binary'];
    } else {
        $file_path= dirname(__FILE__) .'/../static/photo/'. $user_photo['photo_path'];
        if (!file_exists($file_path)) {
            $body = base64_decode("iVBORw0KGgoAAAANSUhEUgAAAIAAAACACAYAAADDPmHLAAAA3klEQVR42u3SAQ0AAAgDIN8/9K3hJmQgnXZ4KwIIIIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACCCAAAIIIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAA3LNkJfxBbCdD2AAAAAElFTkSuQmCC");
        } else {
            $body = file_get_contents($file_path);
        }
    }

    $newResponse = $response->withHeader('Content-type', 'image/png');
    $newResponse->getBody()->write($body);

    return $newResponse;
});

$app->get('/', function (Request $request, Response $response) {
    $page = $request->getParam('page') ?? '1';
    if (!is_numeric($page)) {
        return $response->withStatus(400);
    }
    $page = (int)$page;

    $stmt = dbExecute("SELECT COUNT(*) as cnt FROM articles");
    $cnt = (int)($stmt->fetch()['cnt']);
    $pageSize = 20;
    $maxPage = ceil($cnt / $pageSize);
    if ($maxPage == 0) {
        $maxPage = 1;
    }

    if ($page < 1 || $maxPage < $page) {
        return $response->withStatus(400);
    }

    $offset = ($page - 1) * $pageSize;

    $latestArticleQuery = <<<__SQL
	SELECT
	 *
	FROM
	 articles
	ORDER BY
	 updated_at DESC,
	 id DESC
__SQL;
    $latestArticleQuery  = $latestArticleQuery . "	LIMIT $pageSize OFFSET $offset; ";
    $latestArticles = dbExecute($latestArticleQuery)->fetchAll();

    foreach ($latestArticles as $key => $article) {
        $latestArticles[$key]['author'] = dbGetUser(getPDO(), $article['author_id']);
        $latestArticles[$key]['iineCnt'] = getIineCount($article['id']);
        $latestArticles[$key]['tagNames'] = getArticleTagnames($article['id']);
        $latestArticles[$key]['updateString'] = deltaTime($article['updated_at']);
    }


    $locals = [
        'user' => currentUser($request, $response),
        'articles' => $latestArticles,
        'popular_articles' => getPopularArticles(),
        'online_users' => getLoginUsers(),
        'max_page' => $maxPage,
        'page' => $page
    ];
    $this->view->render($response, 'index.twig', $locals);
})->add($loginRequired);

$app->get('/tags', function (Request $request, Response $response) {
    $page = $request->getParam('page') ?? '1';
    if (!is_numeric($page)) {
        return $response->withStatus(400);
    }
    $page = (int)$page;

    $stmt = dbExecute("SELECT COUNT(*) as cnt FROM tags");
    $cnt = (int)($stmt->fetch()['cnt']);
    $pageSize = 20;
    $maxPage = ceil($cnt / $pageSize);
    if ($maxPage == 0) {
        $maxPage = 1;
    }

    if ($page < 1 || $maxPage < $page) {
        return $response->withStatus(400);
    }

    $offset = ($page - 1) * $pageSize;

    $tagsQuery = <<<__SQL
        SELECT
         *
        FROM
         tags
        ORDER BY
         tagname
__SQL;
    $tagsQuery  = $tagsQuery . "  LIMIT $pageSize OFFSET $offset; ";
    $tags = dbExecute($tagsQuery)->fetchAll();

    foreach ($tags as $key => $tag) {
        $tags[$key]['tagCnt'] = getTagCount($tag['id']);
    }

    $locals = [
        'tags' => $tags,
        'page' => $page,
        'max_page' => $maxPage,
        'max_count' => $cnt
    ];
    $this->view->render($response, 'tags.twig', $locals);
})->add($loginRequired);

$app->get('/tag/{tag_id}', function (Request $request, Response $response) {
    $tagId = $request->getAttribute('tag_id');

    $page = $request->getParam('page') ?? '1';
    if (!is_numeric($page)) {
        return $response->withStatus(400);
    }
    $page = (int)$page;

    $stmt = dbExecute("SELECT COUNT(*) as cnt FROM article_relate_tags WHERE tag_id = ?", [$tagId]);
    $cnt = (int)($stmt->fetch()['cnt']);
    $pageSize = 20;
    $maxPage = ceil($cnt / $pageSize);
    if ($maxPage == 0) {
        $maxPage = 1;
    }

    if ($page < 1 || $maxPage < $page) {
        return $response->withStatus(400);
    }

    $offset = ($page - 1) * $pageSize;


    $tagArticleQuery = <<<__SQL
        SELECT
         *
        FROM
         article_relate_tags
        WHERE
         tag_id = ?
        ORDER BY
         article_id DESC
__SQL;
    $tagArticleQuery  = $tagArticleQuery . "  LIMIT $pageSize OFFSET $offset; ";
    $tagArticles = dbExecute($tagArticleQuery, [$tagId])->fetchAll();

    if (!$tagArticles) {
        return $response->withStatus(404);
    }

    foreach ($tagArticles as $key => $article) {
        $tagArticle = getArticle($article['article_id']);
        $tagArticles[$key]['author'] = dbGetUser(getPDO(), $article['article_id']);
        $tagArticles[$key]['iineCnt'] = getIineCount($article['article_id']);
        $tagArticles[$key]['updateString'] = deltaTime($tagArticle['updated_at']);
        $tagArticles[$key]['tagNames'] = getArticleTagnames($article['article_id']);
        $tagArticles[$key]['article'] = $tagArticle;
    }
    $locals = [
        'user' => currentUser($request, $response),
        'tag_articles' => $tagArticles,
        'tag_id' => $tagId,
        'max_page' => $maxPage,
        'page' => $page
    ];

    $this->view->render($response, 'tag.twig', $locals);
})->add($loginRequired);


$app->get('/members', function (Request $request, Response $response) {
    $page = $request->getParam('page') ?? '1';
    if (!is_numeric($page)) {
        return $response->withStatus(400);
    }
    $page = (int)$page;

    $stmt = dbExecute("SELECT COUNT(*) as cnt FROM users");
    $cnt = (int)($stmt->fetch()['cnt']);
    $pageSize = 20;
    $maxPage = ceil($cnt / $pageSize);
    if ($maxPage == 0) {
        $maxPage = 1;
    }

    if ($page < 1 || $maxPage < $page) {
        return $response->withStatus(400);
    }

    $offset = ($page - 1) * $pageSize;

    $membersQuery = <<<__SQL
        SELECT
         *
        FROM
         users
        ORDER BY
         id
__SQL;
    $membersQuery  = $membersQuery . "  LIMIT $pageSize OFFSET $offset; ";
    $members = dbExecute($membersQuery)->fetchAll();

    foreach ($members as $key => $member) {
        $members[$key]['articleCnt'] = getArticleCount($member['id']);
    }

    $locals = [
        'members' => $members,
        'page' => $page,
        'max_page' => $maxPage,
        'max_count' => $cnt
    ];
    $this->view->render($response, 'members.twig', $locals);
})->add($loginRequired);

$app->get('/member/{member_id}', function (Request $request, Response $response) {
    $memberId = $request->getAttribute('member_id');

    $page = $request->getParam('page') ?? '1';
    if (!is_numeric($page)) {
        return $response->withStatus(400);
    }
    $page = (int)$page;

    $stmt = dbExecute("SELECT COUNT(*) as cnt FROM articles WHERE author_id = ?", [$memberId]);
    $cnt = (int)($stmt->fetch()['cnt']);
    $pageSize = 20;
    $maxPage = ceil($cnt / $pageSize);
    if ($maxPage == 0) {
        $maxPage = 1;
    }

    if ($page < 1 || $maxPage < $page) {
        return $response->withStatus(400);
    }

    $offset = ($page - 1) * $pageSize;

    $memberArticleQuery = <<<__SQL
        SELECT
         *
        FROM
         articles
        WHERE
         author_id = ?
        ORDER BY
         updated_at DESC,
         id DESC
__SQL;
    $memberArticleQuery  = $memberArticleQuery . "  LIMIT $pageSize OFFSET $offset; ";
    $memberArticles = dbExecute($memberArticleQuery, [$memberId])->fetchAll();
    
    $member = dbGetUser(getPDO(), $memberId);
    if (!$member) {
        return $response->withStatus(404);
    }

    foreach ($memberArticles as $key => $article) {
        $memberArticles[$key]['author'] = dbGetUser(getPDO(), $article['author_id']);
        $memberArticles[$key]['iineCnt'] = getIineCount($article['id']);
        $memberArticles[$key]['tagNames'] = getArticleTagnames($article['id']);
        $memberArticles[$key]['updateString'] = deltaTime($article['updated_at']);
    }

    $locals = [
        'user' => currentUser($request, $response),
        'author' => dbGetUser(getPDO(), $memberId),
        'articles' => $memberArticles,
        'page' => $page,
        'max_page' => $maxPage,
        'max_count' => $cnt
    ];
    $this->view->render($response, 'member.twig', $locals);
})->add($loginRequired);

$app->get('/article/{article_id}', function (Request $request, Response $response) {
    $articleId = $request->getAttribute('article_id');
    $userId    = FigRequestCookies::get($request, 'user_id')->getValue();

    $cnt = dbExecute("SELECT COUNT(id) as cnt FROM iines WHERE article_id =? AND user_id = ?", [$articleId, $userId])->fetch()['cnt'];

    $article = getArticle($articleId);
    if (!$article) {
        return $response->withStatus(404);
    }
    
    $article['author'] = dbGetUser(getPDO(), $article['author_id']);
    $article['updateString'] = deltaTime($article['updated_at']);

    $locals = [
        'user' => currentUser($request, $response),
        'article' => $article,
        'tagNames' => getArticleTagnames($articleId),
        'iineCnt' => getIineCount($articleId),
        'iineUsers' => getArticleIineUsers($articleId),
        'doneIine' => $cnt,
    ];
    $this->view->render($response, 'article.twig', $locals);
})->add($loginRequired);

$app->post('/iine/{article_id}', function (Request $request, Response $response) {
    $articleId = $request->getAttribute('article_id');
    $userId    = FigRequestCookies::get($request, 'user_id')->getValue();
    $sign      = $request->getParam('name');

    if ($sign === 'plus') {
        $query = "INSERT INTO iines (article_id, user_id) VALUES( ?, ? );";
        dbExecute($query, [$articleId,$userId]);
    } else {
        $query = "DELETE FROM iines WHERE article_id = ? AND user_id = ?";
        dbExecute($query, [$articleId,$userId]);
    }
    $cnt = dbExecute("SELECT COUNT(id) as cnt FROM iines WHERE article_id = ?", [$articleId])->fetch()['cnt'];
    return $cnt;
});

$app->get('/write', function (Request $request, Response $response) {
    $locals = [
        'user' => currentUser($request, $response),
        'message' => 'Jiriqiのあがる記事を書こう！',
    ];
    $this->view->render($response, 'write.twig', $locals);
})->add($loginRequired);

$app->post('/write', function (Request $request, Response $response) {
    $title       = $request->getParam('title');
    $tags        = $request->getParam('tags');
    $articleBody = $request->getParam('articleBody');

    $userId = FigRequestCookies::get($request, 'user_id')->getValue();

    $dbh = getPDO();

    try {
        $dbh->beginTransaction();

        $articleId = InsArticle($dbh, $userId, $title, $tags, $articleBody);

        $dbh->commit();
    } catch (PDOException $e) {
        $dbh->rollback();
        if ($e->errorInfo[0] === "23000") {
            $locals = [
                'user' => currentUser($request, $response),
                'title' => $title,
                'tags'  => $tags,
                'articleBody' => $articleBody,
                'message' => 'タイトルが重複しています',
            ];
            return $this->view->render($response, 'write.twig', $locals);
        }
    }

    return $response->withRedirect('/article/' . $articleId, 303);
});

$app->get('/update/{ article_id }', function (Request $request, Response $response) {
    $articleId = $request->getAttribute('article_id');
    $userId = FigRequestCookies::get($request, 'user_id')->getValue();

    $article = getArticle($articleId);

    if (!$userId || !$article || $userId != $article['author_id']) {
        return $response->withStatus(403);
    }

    $tagsArray = getArticleTagnames($articleId);
    $tags = "";
    foreach ($tagsArray as $tagId => $tagName) {
        $tags = $tags . "," . $tagName['name'];
    }
   
    $locals = [
        'user' => currentUser($request, $response),
        'title' => $article['title'],
        'tags'  => $tags,
        'articleBody' => $article['description'],
        'article_id' => $articleId,
        'message' => 'Jiriqiのあがる記事の更新',
    ];
    $this->view->render($response, 'update.twig', $locals);
})->add($loginRequired);


$app->post('/update/{ article_id }', function (Request $request, Response $response) {
    $title       = $request->getParam('title');
    $tags        = $request->getParam('tags');
    $articleId   = $request->getParam('article_id');
    $articleBody = $request->getParam('articleBody');

    $userId = FigRequestCookies::get($request, 'user_id')->getValue();
    $article = getArticle($articleId);


    if (!$userId || !$article || $userId != $article['author_id']) {
        return $response->withStatus(403);
    }

    $dbh = getPDO();

    try {
        $dbh->beginTransaction();

        UpdArticle($dbh, $userId, $articleId, $title, $tags, $articleBody);

        $dbh->commit();
    } catch (PDOException $e) {
        $dbh->rollback();
        if ($e->errorInfo[0] === "23000") {
            $locals = [
                'user' => currentUser($request, $response),
                'title' => $title,
                'tags'  => $tags,
                'articleBody' => $articleBody,
                'article_id' => $articleId,
                'message' => 'タイトルが重複しています',
            ];
            return $this->view->render($response, 'update.twig', $locals);
        }
    }

    return $response->withRedirect('/article/' . $articleId, 303);
});

$app->get('/profileupdate/{ user_id }', function (Request $request, Response $response) {
    $userIdByGet = $request->getAttribute('user_id');
    $userId = FigRequestCookies::get($request, 'user_id')->getValue();
    if (!$userId || !$userIdByGet || $userId != $userIdByGet) {
        return $response->withStatus(403);
    }

    $locals = [
        'user' => currentUser($request, $response),
        'message' => "プロフィールを変更します",
    ];
    $this->view->render($response, 'profileupdate.twig', $locals);
})->add($loginRequired);

$app->post('/profileupdate/{ user_id }', function (Request $request, Response $response) {
    $userIdByGet = $request->getAttribute('user_id');
    $userId = FigRequestCookies::get($request, 'user_id')->getValue();
    if (!$userId || !$userIdByGet || $userId != $userIdByGet) {
        return $response->withStatus(403);
    }


    $uploadedFiles    = $request->getUploadedFiles();
    $iconFile         = $uploadedFiles['icon_file'];
    $nickName         = $request->getParam('nick_name');
    $email            = $request->getParam('email');
    $currentPassword  = $request->getParam('current_password');
    $newPassword      = $request->getParam('new_password');

    $dbh = getPDO();

    try {
        $dbh->beginTransaction();

        if ($iconFile->getError() === UPLOAD_ERR_OK && $iconFile->getError()!==UPLOAD_ERR_NO_FILE) {
            $iconBinary = getUploadedFileBinary($iconFile);
            insAndUpdUserPhoto($dbh, $userId, null, $iconBinary);
        }
        if ($newPassword) {
            $errorMessage = updatePassword($dbh, $userId, $currentPassword, $newPassword);
            if ($errorMessage) {
                $locals = [
                    'user' => currentUser($request, $response),
                    'message' => $errorMessage,
                ];
                return $this->view->render($response, 'profileupdate.twig', $locals);
            }
        }

        if ($nickName) {
            updateNickName($dbh, $userId, $nickName);
        }

        if ($email) {
            updateEmail($dbh, $userId, $email);
        }
        $dbh->commit();
    } catch (PDOException $e) {
        $dbh->rollback();
        $locals = [
            'user' => currentUser($request, $response),
            'message' => $e->getMessage(),
        ];
        return $this->view->render($response, 'profileupdate.twig', $locals);
    }
    return $response->withRedirect('/member/' . $userId, 303);
});

$app->get('/initialize', function (Request $request, Response $response) {
    dbExecute("DELETE FROM users WHERE id > 5000");
    dbExecute("DELETE FROM salts WHERE user_id > 5000");
    dbExecute("DELETE FROM user_photos WHERE user_id > 5000");
    dbExecute("DELETE FROM tags WHERE id > 999");
    dbExecute("DELETE FROM iines WHERE id > 50000");
    dbExecute("DELETE FROM articles WHERE id > 7101");
    dbExecute("DELETE FROM article_relate_tags WHERE article_id > 7101");
    $redis = getRedis();
    $redis->flushAll();
    for ($i=1; $i<500; $i++) {
        setLogin($i);
    }
});

$app->run();
