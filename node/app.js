const fs = require('fs');
const path = require('path');
const util = require('util');

const bodyParser = require('body-parser');
const cookieSession = require('cookie-session');
const express = require('express');
const morgan = require('morgan');
const moment = require('moment');
const multer = require('multer');
const mysql = require('mysql2/promise');
const redis = require('redis');

const PORT = process.env.PORT || 8080;

const app = express();

app.use(express.static(__dirname + '/../static'));
app.set('views', path.join(__dirname, 'views'));
app.set('view engine', 'ejs');
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({extended: false}));
app.use(cookieSession({
  name: process.env.HIDAKKATHON_SESSION_NAME || 'hidakkathon',
  secret: process.env.HIDAKKATHON_SESSION_SECRET || 'hidakkathon'
}));

const upload = multer({storage: multer.memoryStorage()}).single('icon_file');

app.use(morgan('dev'));

const mysqlPool = mysql.createPool({
  connectionLimit: 20,
  host: process.env.HIDAKKATHON_DB_HOST || 'localhost',
  port: process.env.HIDAKKATHON_DB_PORT || '3306',
  user: process.env.HIDAKKATHON_DB_USER || 'ubuntu',
  password: process.env.HIDAKKATHON_DB_PASSWORD || 'ubuntu',
  database: process.env.HIDAKKATHON_DB_NAME|| 'hidakkathon',
  charset: 'utf8mb4',
});

const redisClient = (() => {
  const client = redis.createClient({
    host: process.env.HIDAKKATHON_CACHE_HOST || 'localhost',
    port: process.env.HIDAKKATHON_CACHE_PORT || 6379
  });
  return {
    get: util.promisify(client.get).bind(client),
    keys: util.promisify(client.keys).bind(client),
    set: util.promisify(client.set).bind(client),
    del: util.promisify(client.del).bind(client)
  }
})();

const getNow = () => {
  return moment().format('YYYY/MM/DD HH:mm:ss');
};

const dbGetUser = async (userId) => {
  const [rows, fields] = await mysqlPool.query('SELECT * FROM users WHERE id = ?', [userId]);
  return rows && rows[0];
};

const loginRequired = async (req, res, next) => {
  const userId = req.session.userId;
  if (!userId) {
    return res.redirect(303, '/login');
  }
  const user = await dbGetUser(userId);
  if (!user) {
    req.session.userId = null;
    return res.redirect(303, '/login');
  }
  await setLogin(userId);
  req.session.user = user;
  next();
};

const abortAuthenticationError = (req, res) => {
  req.session.userId = null;
  res.status(401).render('login', {message: 'ログインに失敗しました'});
};

const dbExecute = async (query, args, conn) => {
  if (conn) {
    const [rows, fields] = await conn.query(query, args);
    return rows;
  }
  const [rows, fields] = await mysqlPool.query(query, args);
  return rows;
};

const authenticate = async (email, password) => {
  const query = `
    SELECT u.id AS id, u.nick_name AS nick_name, u.email AS email
    FROM users u
    JOIN salts s ON u.id = s.user_id
    WHERE u.email = ? AND u.passhash = SHA2(CONCAT(?, s.salt), 512)`;
  const results = await dbExecute(query, [email, password]);
  return results && results[0] || null;
};

const currentUser = async (req, res) => {
  const userId = req.session.userId;
  const users = await dbExecute('SELECT id, nick_name, email FROM users WHERE id = ?', [userId]);
  if (!users || !users.length) {
    return abortAuthenticationError(req, res);
  }
  return users[0];
};

const setLogin = async (userId) => {
  await redisClient.set(`login_${userId}`, Date.now());
};

const getLoginUsers = async () => {
  const onLineUsers = [];
  const loginUsers = await redisClient.keys('login_*');
  for (const value of loginUsers) {
    const loginTime = await redisClient.get(value);
    if (Date.now() - loginTime < 60 * 60 * 1000) {
      onLineUsers.push(parseInt(value.slice(6)));
    }
  }
  onLineUsers.sort((a, b) => {
    if (a < b) return -1;
    if (a > b) return 1;
    return 0;
  });
  return onLineUsers;
};

const randomString = (length) => {
  let str = '';
  const c = '1234567890abcdef';
  const cl = c.length;
  for (let i = 0; i < length; i++) {
    str += c[Math.floor(Math.random() * cl)];
  }
  return str;
};

const register = async (name, email, password, conn) => {
  const salt = randomString(6);
  let query = 'INSERT INTO users (nick_name, email, passhash) VALUES (?, ?, SHA2(CONCAT(?, ?), 512))';
  await dbExecute(query, [name, email, password, salt], conn);
  query = 'SELECT LAST_INSERT_ID() AS last_insert_id';
  const results = await dbExecute(query, null, conn);
  const userId = results && results[0] && results[0]['last_insert_id'];
  query = 'INSERT INTO salts (user_id, salt) VALUES ( ?, ? )';
  await dbExecute(query, [userId, salt], conn);
  return userId;
};

const deltaTime = (updateTime) => {
  const date = new Date(updateTime);
  const diff = new Date().getTime() - date.getTime();
  const d = new Date(diff);
  if (d.getUTCFullYear() - 1970) {
    return d.getUTCFullYear() - 1970 + '年前'
  } else if (d.getUTCMonth()) {
    return d.getUTCMonth() + 'ヶ月前'
  } else if (d.getUTCDate() - 1) {
    return d.getUTCDate() - 1 + '日前'
  } else if (d.getUTCHours()) {
    return d.getUTCHours() + '時間前'
  } else if (d.getUTCMinutes()) {
    return d.getUTCMinutes() + '分前'
  } else {
    return 'たったいま';
  }
};

const getIineCount = async (articleId) => {
  const query = 'SELECT COUNT(`id`) as cnt FROM iines WHERE article_id = ?';
  const results = await dbExecute(query, [articleId]);
  return results && results[0] && results[0]['cnt'] || 0;
};

const getTagCount = async (tagId) => {
  const query = 'SELECT COUNT(*) as cnt FROM article_relate_tags WHERE tag_id = ?';
  const results = await dbExecute(query, [tagId]);
  return results && results[0] && results[0]['cnt'] || 0;
};

const getArticleCount = async (authorId) => {
  const query = 'SELECT COUNT(*) as cnt FROM articles WHERE author_id = ?';
  const results = await dbExecute(query, [authorId]);
  return results && results[0] && results[0]['cnt'] || 0;
};

const getArticleTagNames = async (articleId) => {
  let tagNames = [];
  const query = 'SELECT tag_id FROM article_relate_tags WHERE article_id = ? ORDER BY tag_id ASC';
  const tagIds = await dbExecute(query, [articleId]);
  for (const tagId of tagIds) {
    const query = 'SELECT tagname FROM tags WHERE id = ?';
    const results = await dbExecute(query, [tagId['tag_id']]);
    if (results && results[0] && results[0]['tagname']) {
      tagNames.push({tagId: tagId['tag_id'], name: results[0]['tagname']});
    }
  }
  return tagNames;
};

const getArticle = async (id) => {
  const query = 'SELECT * FROM articles WHERE id = ?';
  const results = await dbExecute(query, [id]);
  return results && results[0];
};

const getArticleIineUsers = async (id) => {
  const query = 'SELECT user_id FROM iines WHERE article_id = ?';
  return await dbExecute(query, [id]);
};

const getPopularArticles = async () => {
  const query = `
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
    LIMIT 5`;
  const popularArticles = await dbExecute(query);
  await Promise.all(popularArticles.map(async (article) => {
    article['article'] = await getArticle(article['article_id']);
    article['iine_users'] = await getArticleIineUsers(article['article_id']);
  }));
  return popularArticles;
};

const InsArticle = async (userId, title, tags, articleBody, conn) => {
  let query = 'INSERT INTO articles ( author_id, title, description, updated_at, created_at ) VALUES ( ?, ?, ?, ?, ?)';
  await dbExecute(query, [userId, title, articleBody, getNow(), getNow()], conn);

  query = 'SELECT LAST_INSERT_ID() AS last_insert_id';
  let result = await dbExecute(query, null, conn);
  const articleId = result[0]['last_insert_id'];

  if (tags) {
    const tagArray = tags.split(',');

    const articleTagIds = [];
    for (const tag of tagArray) {
      query = 'SELECT id FROM tags WHERE tagname = ?';
      result = await dbExecute(query, [tag.trim()], conn);
      const tagId = result[0]['id'];
      if (tagId) {
        articleTagIds.push(tagId);
      } else {
        query = 'INSERT INTO tags (tagname) VALUES ( ? )';
        await dbExecute(query, [tag.trim()], conn);
        query = 'SELECT LAST_INSERT_ID() AS last_insert_id';
        result = await dbExecute(query, null, conn);
        articleTagIds.push(result[0]['last_insert_id']);
      }
    }

    for (const articleTagId of articleTagIds) {
      query = 'INSERT INTO article_relate_tags (article_id, tag_id) VALUES ( ?, ? )';
      await dbExecute(query, [articleId, articleTagId], conn);
    }
  }
  return articleId;
};

const UpdArticle = async (userId, articleId, title, tags, articleBody, conn) => {
  const query = 'UPDATE articles set title = ? , description = ?, updated_at = ? WHERE id = ?';
  await dbExecute(query, [title, articleBody, getNow(), articleId], conn);

  if (tags) {
    const tagArray = tags.split(',');
    const articleTagIds = [];
    for (const tag of tagArray) {
      const results = await dbExecute('SELECT id FROM tags WHERE tagname = ?', [tag.trim()], conn);
      const tagId = results && results[0] && results[0]['id'];
      if (tagId) {
        articleTagIds.push(tagId);
      } else {
        await dbExecute('INSERT INTO tags (tagname) VALUES ( ? );', [tag.trim()], conn);
        const results = await dbExecute('SELECT LAST_INSERT_ID() AS last_insert_id', null, conn);
        articleTagIds.push(results[0]['last_insert_id']);
      }
    }

    await dbExecute('DELETE FROM article_relate_tags  WHERE article_id = ?', [articleId], conn);

    for (const articleTagId of articleTagIds) {
      const query = 'INSERT INTO article_relate_tags (article_id, tag_id) VALUES ( ?, ? )';
      await dbExecute(query, [articleId, articleTagId], conn);
    }
  }
};

const insAndUpdUserPhoto = async (userId, photoPath, photoBinary) => {
  if (photoBinary) {
    const query = `
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
        updated_at = ?`;
    await dbExecute(query, [userId, photoBinary, getNow(), photoBinary, getNow()]);
  } else if (photoPath) {
    const query = `
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
        updated_at = ?`;
    await dbExecute(query, [userId, photoPath, getNow(), photoPath, getNow()]);
  }
};

const updatePassword = async (userId, currentPassword, newPassword, conn) => {
  let query = `
    SELECT
      COUNT(u.id) AS cnt
    FROM
      users u
    JOIN
      salts s ON u.id = s.user_id
    WHERE
      u.id = ?
    AND
      u.passhash = SHA2(CONCAT(?, s.salt), 512)`;
  const results = await dbExecute(query, [userId, currentPassword], conn);
  const cnt = parseInt(results[0]['cnt']);
  if (cnt !== 1) {
    return '現在のパスワードが違います';
  }
  const salt = randomString(6);
  query = 'UPDATE users SET passhash = SHA2(CONCAT(?, ?), 512) WHERE id = ?';
  await dbExecute(query, [newPassword, salt, userId], conn);
  query = 'UPDATE salts SET salt =  ? WHERE user_id = ?';
  await dbExecute(query, [salt, userId], conn);
};

const updateNickName = async (userId, nickName, conn) => {
  const query = 'UPDATE users SET nick_name = ? WHERE id = ?';
  await dbExecute(query, [nickName, userId], conn);
};

const updateEmail = async (userId, email, conn) => {
  const query = 'UPDATE users SET email = ? WHERE id = ?';
  await dbExecute(query, [email, userId], conn);
};

app.get('/login', (req, res) => {
  res.render('login', {message: 'SGE内で培われた技術や知見を横軸全体で共有するサービス'});
});

app.post('/login', async (req, res) => {
  const email = req.body.email;
  const password = req.body.password;
  const ret = await authenticate(email, password);
  if (!ret) {
    req.session.userId = null;
    return res.render('login', {message: 'ログインに失敗しました'});
  }
  req.session.userId = ret['id'];
  await setLogin(ret['id']);
  res.redirect(303, '/');
});

app.get('/regist', (req, res) => {
  res.render('regist', {message: '新規登録して利用を開始しましょう。'});
});

app.post('/regist', async (req, res) => {
  const name = req.body.username;
  const email = req.body.email;
  const password = req.body.password;
  if (!name || !email || !password) {
    return res.render('regist', {message: '全て必須入力です'});
  }
  const conn = await mysqlPool.getConnection();
  try {
    await conn.beginTransaction();
    req.session.userId = await register(name, email, password, conn);
    await conn.commit();
  } catch (err) {
    await conn.rollback();
    if (err.errno === 1062) {
      return res.render('regist', {message: 'すでに登録済みです'});
    }
    return res.render('regist', {message: err});
  }
  res.redirect(303, '/');
});

app.get('/logout', (req, res) => {
  req.session.userId = null;
  res.redirect(303, '/');
});

app.get('/photo/:member_id', loginRequired, async (req, res) => {
  const memberId = req.params['member_id'];
  const userPhotos = await dbExecute('SELECT * FROM user_photos WHERE user_id = ?', [memberId]);
  const str = 'iVBORw0KGgoAAAANSUhEUgAAAIAAAACACAYAAADDPmHLAAAA3klEQVR42u3SAQ0AAAgDIN8/9K3hJmQgnXZ4KwIIII' +
    'AACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACI' +
    'AACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACCCAAAIIIAACIAACIAACIAACIAACIAACIA' +
    'ACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAACIAA3LNkJfxBbCdD2AAAAAElFTkSuQmCC';
  let img = '';
  if (!userPhotos || !userPhotos.length) {
    img = new Buffer(str, 'base64');
  } else if (userPhotos[0]['photo_binary']) {
    img = new Buffer(userPhotos[0]['photo_binary'], 'base64');
  } else {
    const filePath = __dirname + '/../static/photo/' + userPhotos[0]['photo_path'];
    try {
      img = fs.readFileSync(filePath);
    } catch (err) {
      img = new Buffer(str, 'base64');
    }
  }
  res.set({'Content-Length': img.length});
  res.send(img);
});

app.get('/', loginRequired, async (req, res) => {
  let page = req.query.page || 1;
  if (isNaN(page)) {
    return res.status(400).end();
  }
  page = parseInt(page);

  const stmt = await dbExecute('SELECT COUNT(*) as cnt FROM articles');
  const cnt = parseInt((stmt[0]['cnt']));
  const pageSize = 20;
  const maxPage = Math.ceil(cnt / pageSize) || 1;

  if (page < 1 || maxPage < page) {
    return res.status(400).end();
  }

  const offset = (page - 1) * pageSize;

  const latestArticleQuery = `
    SELECT
     *
    FROM
     articles
    ORDER BY
     updated_at DESC,
    id DESC
    LIMIT ${pageSize}
    OFFSET ${offset}`;
  const latestArticles = await dbExecute(latestArticleQuery);
  await Promise.all(latestArticles.map(async (article) => {
    article['author'] = await dbGetUser(article['author_id']);
    article['iineCnt'] = await getIineCount(article['id']);
    article['tagNames'] = await getArticleTagNames(article['id']);
    article['updateString'] = deltaTime(article['updated_at']);
  }));

  const locals = {
    user: await currentUser(req, res),
    articles: latestArticles,
    popular_articles: await getPopularArticles(),
    online_users: await getLoginUsers(),
    max_page: maxPage,
    page: page
  };
  res.render('index', locals);
});

app.get('/tags', loginRequired, async (req, res) => {
  let page = req.query['page'] || 1;
  if (isNaN(page)) {
    return res.status(400).end();
  }
  page = parseInt(page);

  const results = await dbExecute('SELECT COUNT(*) as cnt FROM tags');
  const cnt = parseInt(results[0]['cnt']);
  const pageSize = 20;
  const maxPage = Math.ceil(cnt / pageSize) || 1;

  if (page < 1 || maxPage < page) {
    return res.status(400).end();
  }

  const offset = (page - 1) * pageSize;

  const tagsQuery = `
    SELECT
      *
    FROM
      tags
    ORDER BY
      tagname
    LIMIT ${pageSize} OFFSET ${offset}`;
  const tags = await dbExecute(tagsQuery);

  await Promise.all(tags.map(async (tag) => {
    tag['tagCnt'] = await getTagCount(tag['id']);
  }));

  const locals = {
    user: await currentUser(req, res),
    tags: tags,
    page: page,
    max_page: maxPage,
    max_count: cnt
  };
  res.render('tags', locals);
});

app.get('/tag/:tag_id', loginRequired, async (req, res) => {
  const tagId = req.params['tag_id'];

  let page = req.query['page'] || 1;
  if (isNaN(page)) {
    return res.status(400).end();
  }
  page = parseInt(page);

  const results = await dbExecute('SELECT COUNT(*) as cnt FROM article_relate_tags WHERE tag_id = ?', [tagId]);
  const cnt = parseInt(results[0]['cnt']);
  const pageSize = 20;
  const maxPage = Math.ceil(cnt / pageSize) || 1;

  if (page < 1 || maxPage < page) {
    return res.status(400).end();
  }

  const offset = (page - 1) * pageSize;

  const tagArticleQuery = `
    SELECT
      *
    FROM
      article_relate_tags
    WHERE
      tag_id = ?
    ORDER BY
      article_id DESC
    LIMIT ${pageSize} OFFSET ${offset}`;
  const tagArticles = await dbExecute(tagArticleQuery, [tagId]);

  if (!tagArticles || !tagArticles.length) {
    return res.status(404).end();
  }

  await Promise.all(tagArticles.map(async (article) => {
    const tagArticle = await getArticle(article['article_id']);
    article['author'] = await dbGetUser(tagArticle['author_id']);
    article['iineCnt'] = await getIineCount(article['article_id']);
    article['updateString'] = deltaTime(tagArticle['updated_at']);
    article['tagNames'] = await getArticleTagNames(article['article_id']);
    article['article'] = tagArticle;
  }));
  const locals = {
    user: await currentUser(req, res),
    tag_articles: tagArticles,
    tag_id: tagId,
    max_page: maxPage,
    page: page
  };
  res.render('tag', locals);
});

app.get('/members', loginRequired, async (req, res) => {
  let page = req.query.page || 1;
  if (isNaN(page)) {
    return res.status(404).end();
  }
  page = parseInt(page);

  const stmt = await dbExecute('SELECT COUNT(*) as cnt FROM users');
  const cnt = parseInt(stmt[0]['cnt']);
  const pageSize = 20;
  const maxPage = Math.ceil(cnt / pageSize) || 1;

  if (page < 1 || maxPage < page) {
    return res.status(400).end();
  }

  const offset = (page - 1) * pageSize;

  const membersQuery = `
    SELECT
      *
    FROM
      users
    ORDER BY
      id
    LIMIT ${pageSize} OFFSET ${offset}`;
  const members = await dbExecute(membersQuery);
  await Promise.all(members.map(async (member) => {
    member['articleCnt'] = await getArticleCount(member['id']);
  }));

  const locals = {
    user: await currentUser(req, res),
    members: members,
    page: page,
    max_page: maxPage,
    max_count: cnt
  };
  res.render('members', locals);
});

app.get('/member/:member_id', loginRequired, async (req, res) => {
  const memberId = req.params['member_id'];
  let page = req.query['page'] || 1;
  if (isNaN(page)) {
    return res.status(404).end();
  }
  page = parseInt(page);

  const stmt = await dbExecute('SELECT COUNT(*) as cnt FROM articles WHERE author_id = ?', [memberId]);
  const cnt = parseInt(stmt[0]['cnt']);
  const pageSize = 20;
  const maxPage = Math.ceil(cnt / pageSize) || 1;

  if (page < 1 || maxPage < page) {
    return res.status(400).end();
  }

  const offset = (page - 1) * pageSize;

  const memberArticleQuery = `
    SELECT
      *
    FROM
      articles
    WHERE
      author_id = ?
    ORDER BY
      updated_at DESC,
      id DESC
    LIMIT ${pageSize} OFFSET ${offset};`;
  const memberArticles = await dbExecute(memberArticleQuery, [memberId]);

  const member = await dbGetUser(memberId);
  if (!member) {
    return res.status(404).end();
  }

  await Promise.all(memberArticles.map(async (article) => {
    article['author'] = await dbGetUser(article['author_id']);
    article['iineCnt'] = await getIineCount(article['id']);
    article['tagNames'] = await getArticleTagNames(article['id']);
    article['updateString'] = deltaTime(article['updated_at']);
  }));

  const locals = {
    user: await currentUser(req, res),
    author: await dbGetUser(memberId),
    articles: memberArticles,
    page: page,
    max_page: maxPage,
    max_count: cnt
  };
  res.render('member', locals);
});

app.get('/article/:article_id', loginRequired, async (req, res) => {
  const articleId = req.params['article_id'];
  const userId = req.session.userId;

  const query = 'SELECT COUNT(id) as cnt FROM iines WHERE article_id =? AND user_id = ?';
  const result = await dbExecute(query, [articleId, userId]);
  const cnt = result[0]['cnt'];

  const article = await getArticle(articleId);
  if (!article) {
    return res.status(404).end();
  }

  article['author'] = await dbGetUser(article['author_id']);
  article['updateString'] = deltaTime(article['updated_at']);

  const locals = {
    user: await currentUser(req, res),
    article: article,
    tagNames: await getArticleTagNames(articleId),
    iineCnt: await getIineCount(articleId),
    iineUsers: await getArticleIineUsers(articleId),
    doneIine: cnt
  };
  res.render('article', locals);
});

app.post('/iine/:article_id', async (req, res) => {
  const articleId = req.params['article_id'];
  const userId = req.session.userId;
  const sign = req.body['name'];

  if (sign === 'plus') {
    const query = 'INSERT INTO iines (article_id, user_id) VALUES( ?, ? )';
    await dbExecute(query, [articleId, userId]);
  } else {
    const query = 'DELETE FROM iines WHERE article_id = ? AND user_id = ?';
    await dbExecute(query, [articleId, userId]);
  }
  await dbExecute('SELECT COUNT(id) as cnt FROM iines WHERE article_id = ?', [articleId]);
  res.end();
});

app.get('/write', loginRequired, async (req, res) => {
  const locals = {
    user: await currentUser(req, res),
    message: 'Jiriqiのあがる記事を書こう！',
    title: '',
    tags: '',
    articleBody: '',
  };
  res.render('write', locals);
});

app.post('/write', async (req, res) => {
  const title = req.body['title'];
  const tags = req.body['tags'];
  const articleBody = req.body['articleBody'];

  const userId = req.session.userId;

  const conn = await mysqlPool.getConnection();
  let articleId;
  try {
    await conn.beginTransaction();
    articleId = await InsArticle(userId, title, tags, articleBody, conn);
    await conn.commit();
  } catch (err) {
    await conn.rollback();
    const locals = {
      user: await currentUser(req, res),
      title: title,
      tags: tags,
      articleBody: articleBody,
      message: 'タイトルが重複しています'
    };
    return res.render('write', locals);
  }
  res.redirect(303, `/article/${articleId}`);
});

app.get('/update/:article_id', loginRequired, async (req, res) => {
  const articleId = req.params['article_id'];
  const userId = req.session.userId;

  const article = await getArticle(articleId);

  if (!userId || !article || userId !== article['author_id']) {
    return res.status(403).end();
  }

  const tagsArray = await getArticleTagNames(articleId);
  let tags = '';
  for (const tagName of tagsArray) {
    tags += tagName['name'];
  }

  const locals = {
    user: await currentUser(req, res),
    title: article['title'],
    tags: tags,
    articleBody: article['description'],
    article_id: articleId,
    message: 'Jiriqiのあがる記事の更新',
  };
  res.render('update', locals);
});

app.post('/update/:article_id', async (req, res) => {
  const title = req.body['title'];
  const tags = req.body['tags'];
  const articleId = req.params['article_id'];
  const articleBody = req.body['articleBody'];

  const userId = req.session.userId;
  const article = await getArticle(articleId);

  if (!userId || !article || userId !== article['author_id']) {
    return res.status(403).end();
  }

  const conn = await mysqlPool.getConnection();
  try {
    await conn.beginTransaction();
    await UpdArticle(userId, articleId, title, tags, articleBody, conn);
    await conn.commit();
  } catch (err) {
    await conn.rollback();
    const locals = {
      user: await currentUser(req, res),
      title: title,
      tags: tags,
      articleBody: articleBody,
      article_id: articleId,
      message: 'タイトルが重複しています'
    };
    return res.render('update', locals);
  }
  res.redirect(303, `/article/${articleId}`);
});

app.get('/profileupdate/:user_id', loginRequired, async (req, res) => {
  const userIdByGet = req.params['user_id'];
  const userId = req.session.userId;
  if (!userId || !userIdByGet || userId !== parseInt(userIdByGet)) {
    return res.status(403).end();
  }
  const locals = {
    user: await currentUser(req, res),
    message: 'プロフィールを変更します',
  };
  res.render('profileupdate', locals);
});

app.post('/profileupdate/:user_id', (req, res) => {
  const userIdByGet = req.params['user_id'];
  const userId = req.session.userId;
  if (!userId || !userIdByGet || userId !== parseInt(userIdByGet)) {
    return res.status(403).end();
  }

  upload(req, res, async (err) => {
    const nickName = req.body['nick_name'];
    const email = req.body['email'];
    const currentPassword = req.body['current_password'];
    const newPassword = req.body['new_password'];

    const conn = await mysqlPool.getConnection();
    try {
      await conn.beginTransaction();
      if (req.file) {
        await insAndUpdUserPhoto(userId, null, req.file.buffer);
      }
      if (newPassword) {
        const errorMessage = await updatePassword(userId, currentPassword, newPassword, conn);
        if (errorMessage) {
          const locals = {
            user: await currentUser(req, res),
            message: errorMessage
          };
          return res.render('profileupdate', locals);
        }
      }
      if (nickName) {
        await updateNickName(userId, nickName, conn);
      }
      if (email) {
        await updateEmail(userId, email, conn);
      }
      await conn.commit();
    } catch (err) {
      await conn.rollback();
      const locals = {
        user: await currentUser(req, res),
        message: err.message
      };
      return res.render('profileupdate', locals);
    }
    return res.redirect(303, `/member/${userId}`);
  });
});

app.get('/initialize', async (req, res) => {
  await dbExecute('DELETE FROM users WHERE id > 5000');
  await dbExecute('DELETE FROM user_photos WHERE user_id > 5000');
  await dbExecute('DELETE FROM tags WHERE id > 999');
  await dbExecute('DELETE FROM iines WHERE id > 50000');
  await dbExecute('DELETE FROM articles WHERE id > 7101');
  await dbExecute('DELETE FROM article_relate_tags WHERE article_id > 7101');
  await dbExecute('DELETE FROM salts WHERE user_id > 5000');
  const keys = await redisClient.keys('*');
  await Promise.all(keys.map(async (id) => {
    await redisClient.del(id);
  }));
  for (let i = 1; i < 500; i++) {
    await setLogin(i);
  }
  res.send('OK');
});

app.use((err, req, res, next) => {
  res.status(500).end()
});

app.listen(PORT, async () => {
  await mysqlPool.query('SET SESSION sql_mode="TRADITIONAL,NO_AUTO_VALUE_ON_ZERO,ONLY_FULL_GROUP_BY"');
  console.log(`Example app listening on port ${PORT} !`)
});
