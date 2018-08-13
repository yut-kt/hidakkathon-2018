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
public class TagRepository {
	@Autowired
	NamedParameterJdbcTemplate jdbcTemplate;

	RowMapper<Tag> rowMapper = (rs, i) -> {
		Tag tag = new Tag();
		tag.setId(rs.getInt("id"));
		tag.setTagname(rs.getString("tagname"));
		tag.setCreatedAt(rs.getDate("created_at"));
		return tag;
	};

	public Integer count() {
		try {
			return jdbcTemplate.queryForObject("SELECT COUNT(*) AS cnt FROM tags", new MapSqlParameterSource(),
					Integer.class);
		} catch (EmptyResultDataAccessException e) {
			return 0;
		}
	}

	public Tag find(int tagId) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("id", tagId);

		try {
			return jdbcTemplate.queryForObject("SELECT * FROM tags WHERE id = :id", source, rowMapper);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public Tag find(String tagName) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("tagname", tagName);

		try {
			return jdbcTemplate.queryForObject("SELECT * FROM tags WHERE tagname = :tagname", source, rowMapper);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public List<Tag> findAll(int offset, int limit) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("limit", limit)
				.addValue("offset", offset);

		try {
			return jdbcTemplate.query("SELECT * FROM tags ORDER BY tagname LIMIT :limit OFFSET :offset",
					source, rowMapper);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public Integer register(String tag) {
		KeyHolder keyHolder = new GeneratedKeyHolder();

		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("tagname", tag);
		jdbcTemplate.update("INSERT INTO tags (tagname) VALUES (:tagname)", source, keyHolder);
		return keyHolder.getKey().intValue();
	}
}
