package hidakkathon2018.model;

import java.io.*;
import java.util.*;

public class ArticleRelateTag implements Serializable {
	private static final long serialVersionUID = 1401445379788400662L;

	private Integer articleId;
	private Integer tagId;

	private Article article;
	private User author;
	private Integer iineCount;
	private String deltaTime;
	private List<Tag> tags;

	public Integer getArticleId() {
		return articleId;
	}
	public void setArticleId(Integer articleId) {
		this.articleId = articleId;
	}
	public Integer getTagId() {
		return tagId;
	}
	public void setTagId(Integer tagId) {
		this.tagId = tagId;
	}
	public Article getArticle() {
		return article;
	}
	public void setArticle(Article article) {
		this.article = article;
	}
	public User getAuthor() {
		return author;
	}
	public void setAuthor(User author) {
		this.author = author;
	}
	public Integer getIineCount() {
		return iineCount;
	}
	public void setIineCount(Integer iineCount) {
		this.iineCount = iineCount;
	}
	public String getDeltaTime() {
		return deltaTime;
	}
	public void setDeltaTime(String deltaTime) {
		this.deltaTime = deltaTime;
	}
	public List<Tag> getTags() {
		return tags;
	}
	public void setTags(List<Tag> tags) {
		this.tags = tags;
	}
}
