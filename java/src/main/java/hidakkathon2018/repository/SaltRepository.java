package hidakkathon2018.repository;

import org.springframework.beans.factory.annotation.*;
import org.springframework.dao.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.*;

import hidakkathon2018.model.*;

@Repository
public class SaltRepository {
	@Autowired
	NamedParameterJdbcTemplate jdbcTemplate;

	RowMapper<Salt> rowMapper = (rs, i) -> {
		Salt salt = new Salt();
		salt.setUserId(rs.getInt("user_id"));
		salt.setSalt(rs.getString("salt"));
		return salt;
	};

	public Salt find(int userId) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("user_id", userId);

		try {
			return jdbcTemplate.queryForObject("SELECT * FROM salts WHERE user_id = :user_id", source, rowMapper);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public void register(int userId, String salt) {
		KeyHolder keyHolder = new GeneratedKeyHolder();

		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("user_id", userId)
				.addValue("salt", salt);
		try {
			jdbcTemplate.update("INSERT INTO salts VALUES (:user_id, :salt)", source, keyHolder);
		} catch (Exception e) { }
	}

	public void update(int userId, String salt) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("user_id", userId)
				.addValue("salt", salt);
		try {
			jdbcTemplate.update("UPDATE salts SET salt = :salt WHERE user_id = :user_id", source);
		} catch (Exception e) { }
	}
}
