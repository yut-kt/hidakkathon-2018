package hidakkathon2018.model;

import java.io.*;
import java.util.*;

public class Article implements Serializable {
	private static final long serialVersionUID = -2689758661990497376L;

	private Integer id;
	private Integer authorId;
	private String title;
	private String description;
	private Date updatedAt;
	private Date createdAt;

	private User user;
	private String deltaTime;
	private Integer iineCount;
	private List<Tag> tags;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getAuthorId() {
		return authorId;
	}

	public void setAuthorId(Integer authorId) {
		this.authorId = authorId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Date getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Date updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Date getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public String getDeltaTime() {
		return deltaTime;
	}

	public void setDeltaTime(String deltaTime) {
		this.deltaTime = deltaTime;
	}

	public Integer getIineCount() {
		return iineCount;
	}

	public void setIineCount(Integer iineCount) {
		this.iineCount = iineCount;
	}

	public List<Tag> getTags() {
		return tags;
	}

	public void setTags(List<Tag> tags) {
		this.tags = tags;
	}
}
