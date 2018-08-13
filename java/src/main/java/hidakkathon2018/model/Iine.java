package hidakkathon2018.model;

import java.io.*;
import java.util.*;

public class Iine implements Serializable {
	private static final long serialVersionUID = 5716544658546540229L;

	private Integer id;
	private Integer articleId;
	private Integer userId;
	private Date updatedAt;

	public Integer getId() {
		return id;
	}
	public void setId(Integer id) {
		this.id = id;
	}
	public Integer getArticleId() {
		return articleId;
	}
	public void setArticleId(Integer articleId) {
		this.articleId = articleId;
	}
	public Integer getUserId() {
		return userId;
	}
	public void setUserId(Integer userId) {
		this.userId = userId;
	}
	public Date getUpdatedAt() {
		return updatedAt;
	}
	public void setUpdatedAt(Date updatedAt) {
		this.updatedAt = updatedAt;
	}
}
