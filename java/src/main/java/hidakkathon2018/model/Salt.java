package hidakkathon2018.model;

import java.io.Serializable;

public class Salt implements Serializable {
	private static final long serialVersionUID = -783128712227125052L;

	private Integer userId;
	private String salt;

	public Integer getUserId() {
		return userId;
	}
	public void setUserId(Integer userId) {
		this.userId = userId;
	}
	public String getSalt() {
		return salt;
	}
	public void setSalt(String salt) {
		this.salt = salt;
	}
}
