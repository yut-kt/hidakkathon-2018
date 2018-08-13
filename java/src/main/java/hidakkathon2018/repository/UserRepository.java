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
public class UserRepository {
	@Autowired
	NamedParameterJdbcTemplate jdbcTemplate;

	RowMapper<User> rowMapper = (rs, i) -> {
		User user = new User();
		user.setId(rs.getInt("id"));
		user.setNickName(rs.getString("nick_name"));
		user.setEmail(rs.getString("email"));
		return user;
	};

	public Integer count() {
		try {
			return jdbcTemplate.queryForObject("SELECT COUNT(*) AS cnt FROM users", new MapSqlParameterSource(), Integer.class);
		} catch (EmptyResultDataAccessException e) {
			return 0;
		}
	}

	public User find(int userId) {
		SqlParameterSource source = new MapSqlParameterSource().addValue("id", userId);
		try {
			return jdbcTemplate.queryForObject("SELECT * FROM users WHERE id = :id", source, rowMapper);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public User find(int userId, String password, String salt) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("id", userId)
				.addValue("password", password)
				.addValue("salt", salt);
		try {
			return jdbcTemplate.queryForObject("SELECT * FROM users WHERE id = :id AND passhash = SHA2(CONCAT(:password, :salt), 512)", source, rowMapper);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public List<User> findAll(int offset, int limit) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("limit", limit)
				.addValue("offset", offset);

		try {
			return jdbcTemplate.query("SELECT * FROM users ORDER BY id LIMIT :limit OFFSET :offset", source, rowMapper);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public Integer register(String userName, String email, String password, String salt) {
		KeyHolder keyHolder = new GeneratedKeyHolder();

		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("nick_name", userName)
				.addValue("email", email)
				.addValue("password", password)
				.addValue("salt", salt);
		try {
			jdbcTemplate.update("INSERT INTO users (nick_name, email, passhash) VALUES (:nick_name, :email, SHA2(CONCAT(:password, :salt), 512))", source, keyHolder);
			return keyHolder.getKey().intValue();
		} catch (Exception e) {
			return null;
		}
	}

	public void updatePassword(int userId, String password, String salt) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("id", userId)
				.addValue("password", password)
				.addValue("salt", salt);

		try {
			jdbcTemplate.update("UPDATE users SET passhash = SHA2(CONCAT(:password, :salt), 512) WHERE id = :id", source);
		} catch (Exception e) { }
	}

	public void updateNickname(int userId, String nickName) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("id", userId)
				.addValue("nick_name", nickName);

		try {
			jdbcTemplate.update("UPDATE users SET nick_name = :nick_name WHERE id = :id", source);
		} catch (Exception e) { }
	}

	public void updateEmail(int userId, String email) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("id", userId)
				.addValue("email", email);

		try {
			jdbcTemplate.update("UPDATE users SET email = :email WHERE id = :id", source);
		} catch (Exception e) { }
	}

	public User findByEmailAndRawPassword(String email, String rawPassword) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("email", email)
				.addValue("password", rawPassword);

		try {
			return jdbcTemplate.queryForObject(
					"SELECT u.id AS id, u.nick_name AS nick_name, u.email AS email" + " FROM users u"
							+ " JOIN salts s ON u.id = s.user_id"
							+ " WHERE u.email = :email AND u.passhash = SHA2(CONCAT(:password, s.salt), 512)",
					source, rowMapper);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

}
