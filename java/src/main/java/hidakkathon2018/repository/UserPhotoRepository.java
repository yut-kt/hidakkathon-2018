package hidakkathon2018.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import hidakkathon2018.model.UserPhoto;

@Repository
public class UserPhotoRepository {
	@Autowired
	NamedParameterJdbcTemplate jdbcTemplate;

	RowMapper<UserPhoto> rowMapper = (rs, i) -> {
		UserPhoto userPhoto = new UserPhoto();
		userPhoto.setUserId(rs.getInt("user_id"));
		userPhoto.setPhotoPath(rs.getString("photo_path"));
		userPhoto.setPhotoBinary(rs.getBytes("photo_binary"));
		return userPhoto;
	};

	public UserPhoto find(int userId) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("user_id", userId);

		try {
			return jdbcTemplate.queryForObject("SELECT * FROM user_photos WHERE user_id = :user_id", source, rowMapper);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public void save(int userId, byte[] photoBinary) {
		SqlParameterSource source = new MapSqlParameterSource()
				.addValue("user_id", userId)
				.addValue("photo_binary", photoBinary);

		jdbcTemplate.update(
				"INSERT INTO user_photos (user_id, photo_binary) "
				 + "VALUES (:user_id, :photo_binary) "
				 + "ON DUPLICATE KEY UPDATE photo_binary = :photo_binary" , source);
	}
}
