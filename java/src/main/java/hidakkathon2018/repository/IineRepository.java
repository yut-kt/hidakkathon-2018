package hidakkathon2018.repository;

import java.util.*;

import org.springframework.beans.factory.annotation.*;
import org.springframework.dao.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.*;

import hidakkathon2018.model.*;

@Repository
public class IineRepository {
	@Autowired
	NamedParameterJdbcTemplate jdbcTemplate;

	RowMapper<Iine> rowMapper = (rs, i) -> {
		Iine iine = new Iine();
		iine.setId(rs.getInt("id"));
		iine.setArticleId(rs.getInt("article_id"));
		iine.setUserId(rs.getInt("user_id"));
		iine.setUpdatedAt(rs.getDate("updated_at"));
		return iine;
	};

	public Integer count(int articleId) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("article_id", articleId);

		try {
			return jdbcTemplate.queryForObject("SELECT COUNT(*) AS cnt FROM iines WHERE article_id = :article_id", source, Integer.class);
		} catch (EmptyResultDataAccessException e) {
			return 0;
		}
	}

	public Integer count(int articleId, int userId) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("article_id", articleId)
				.addValue("user_id", userId);

		try {
			return jdbcTemplate.queryForObject("SELECT COUNT(*) AS cnt FROM iines WHERE article_id = :article_id AND user_id = :user_id", source, Integer.class);
		} catch (EmptyResultDataAccessException e) {
			return 0;
		}
	}

	public List<Iine> findAll(int articleId) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("article_id", articleId);

		try {
			return jdbcTemplate.query("SELECT * FROM iines WHERE article_id = :article_id", source, rowMapper);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public void register(int articleId, int userId) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("article_id", articleId)
				.addValue("user_id", userId);

		try {
			jdbcTemplate.update("INSERT INTO iines (article_id, user_id) VALUES (:article_id, :user_id)", source);
		} catch (EmptyResultDataAccessException e) { }
	}

	public void delete(int articleId, int userId) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("article_id", articleId)
				.addValue("user_id", userId);

		try {
			jdbcTemplate.update("DELETE FROM iines WHERE article_id = :article_id AND user_id = :user_id", source);
		} catch (EmptyResultDataAccessException e) { }
	}
}
