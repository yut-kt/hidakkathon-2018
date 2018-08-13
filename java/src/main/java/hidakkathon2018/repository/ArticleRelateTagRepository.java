package hidakkathon2018.repository;

import java.util.*;

import org.springframework.beans.factory.annotation.*;
import org.springframework.dao.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.*;

import hidakkathon2018.model.*;

@Repository
public class ArticleRelateTagRepository {
	@Autowired
	NamedParameterJdbcTemplate jdbcTemplate;

	RowMapper<ArticleRelateTag> rowMapper = (rs, i) -> {
		ArticleRelateTag art = new ArticleRelateTag();
		art.setArticleId(rs.getInt("article_id"));
		art.setTagId(rs.getInt("tag_id"));
		return art;
	};

	public Integer count(int tagId) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("tag_id", tagId);

		try {
			return jdbcTemplate.queryForObject("SELECT COUNT(*) AS cnt FROM article_relate_tags WHERE tag_id = :tag_id", source, Integer.class);
		} catch (EmptyResultDataAccessException e) {
			return 0;
		}
	}

	public List<ArticleRelateTag> findAllByArticle(int articleId) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("article_id", articleId);

		try {
			return jdbcTemplate.query("SELECT * FROM article_relate_tags WHERE article_id = :article_id", source, rowMapper);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public List<ArticleRelateTag> findAllByTag(int tagId, int offset, int limit) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("tag_id", tagId)
				.addValue("limit", limit)
				.addValue("offset", offset);

		try {
			return jdbcTemplate.query("SELECT * FROM article_relate_tags WHERE tag_id = :tag_id ORDER BY article_id DESC LIMIT :limit OFFSET :offset",
					source, rowMapper);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public void register(int articleId, int tagId) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("article_id", articleId)
				.addValue("tag_id", tagId);

		jdbcTemplate.update("INSERT INTO article_relate_tags (article_id, tag_id) VALUES (:article_id, :tag_id)", source);
	}

	public void delete(int articleId) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("article_id", articleId);

		jdbcTemplate.update("DELETE FROM article_relate_tags WHERE article_id = :article_id", source);
	}
}
