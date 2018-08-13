package hidakkathon2018.repository;

import java.util.*;
import java.util.stream.*;

import org.springframework.beans.factory.annotation.*;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.*;

@Repository
public class LoginUserRepository {
	@Autowired
	StringRedisTemplate redisTemplate;

	public void set(int userId) {
		redisTemplate.opsForValue().set("login_" + userId, String.valueOf(new Date().getTime()));
	}

	public List<Integer> findAll() {
		return redisTemplate.keys("login_*").stream()
				.filter(r -> new Date().getTime() - Long.parseLong(redisTemplate.opsForValue().get(r)) < 60 * 60 * 1000)
				.map(r -> Integer.parseInt(r.substring(6)))
		        .sorted(Comparator.naturalOrder())
				.collect(Collectors.toList());
	}

	public void flushAll() {
		redisTemplate.keys("login_*").stream().forEach(r -> redisTemplate.delete(r));
	}
}
