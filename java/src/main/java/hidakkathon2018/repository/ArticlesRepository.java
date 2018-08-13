package hidakkathon2018.repository;

import java.util.*;

import org.springframework.beans.factory.annotation.*;
import org.springframework.dao.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.*;

import hidakkathon2018.model.*;

@Repository
public class ArticlesRepository {
	@Autowired
	NamedParameterJdbcTemplate jdbcTemplate;

	RowMapper<Article> rowMapper = (rs, i) -> {
		Article article = new Article();
		article.setId(rs.getInt("id"));
		article.setAuthorId(rs.getInt("author_id"));
		article.setTitle(rs.getString("title"));
		article.setDescription(rs.getString("description"));
		article.setCreatedAt(rs.getDate("created_at"));
		article.setUpdatedAt(rs.getDate("updated_at"));
		return article;
	};

	public Integer count() {
		try {
			return jdbcTemplate.queryForObject("SELECT COUNT(*) AS cnt FROM articles", new MapSqlParameterSource(), Integer.class);
		} catch (EmptyResultDataAccessException e) {
			return 0;
		}
	}

	public Integer count(int authorId) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("author_id", authorId);

		try {
			return jdbcTemplate.queryForObject("SELECT COUNT(*) AS cnt FROM articles WHERE author_id = :author_id", source, Integer.class);
		} catch (EmptyResultDataAccessException e) {
			return 0;
		}
	}

	public List<Article> findAll(int offset, int limit) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("limit", limit)
				.addValue("offset", offset);

		try {
			return jdbcTemplate.query("SELECT * FROM articles ORDER BY updated_at DESC, id DESC LIMIT :limit OFFSET :offset",
					source, rowMapper);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public List<Article> findAll(int authorId, int offset, int limit) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("author_id", authorId)
				.addValue("limit", limit)
				.addValue("offset", offset);

		try {
			return jdbcTemplate.query("SELECT * FROM articles WHERE author_id = :author_id ORDER BY updated_at DESC, id DESC LIMIT :limit OFFSET :offset",
					source, rowMapper);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public Article find(int articleId) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("id", articleId);
		try {
			return jdbcTemplate.queryForObject("SELECT * FROM articles WHERE id = :id", source, rowMapper);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public Integer register(int userId, String title, String articleBody) {
		KeyHolder keyHolder = new GeneratedKeyHolder();

		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("author_id", userId)
				.addValue("title", title)
				.addValue("description", articleBody)
				.addValue("created_at", new Date())
				.addValue("updated_at", new Date());

		jdbcTemplate.update(
				"INSERT INTO articles (author_id, title, description, updated_at, created_at)"
				 + "VALUES (:author_id, :title, :description, :updated_at, :created_at)", source, keyHolder);
		return keyHolder.getKey().intValue();
	}

	public void update(int articleId, String title, String articleBody) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("article_id", articleId)
				.addValue("title", title)
				.addValue("description", articleBody)
				.addValue("updated_at", new Date());

		jdbcTemplate.update(
				"UPDATE articles "
				 + "SET title = :title, description = :description, updated_at = :updated_at "
				 + "WHERE id = :article_id", source);
	}
}
