package hidakkathon2018.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class UserPhoto implements Serializable {
	private static final long serialVersionUID = -6530917236833791106L;

	private Integer userId;
	private String photoPath;
	private byte[] photoBinary;
	private LocalDateTime updatedAt;

	public Integer getUserId() {
		return userId;
	}
	public void setUserId(Integer userId) {
		this.userId = userId;
	}
	public String getPhotoPath() {
		return photoPath;
	}
	public void setPhotoPath(String photoPath) {
		this.photoPath = photoPath;
	}
	public byte[] getPhotoBinary() {
		return photoBinary;
	}
	public void setPhotoBinary(byte[] photoBinary) {
		this.photoBinary = photoBinary;
	}
	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
}
