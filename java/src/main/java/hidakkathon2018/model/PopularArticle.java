package hidakkathon2018.model;

import java.io.*;
import java.util.*;

public class PopularArticle implements Serializable {
	private static final long serialVersionUID = -3240581206553062056L;

	private Integer articleId;
	private Integer iineCount;

	private Article article;
	private List<Iine> iineUsers;

	public Integer getArticleId() {
		return articleId;
	}
	public void setArticleId(Integer articleId) {
		this.articleId = articleId;
	}
	public Integer getIineCount() {
		return iineCount;
	}
	public void setIineCount(Integer iineCount) {
		this.iineCount = iineCount;
	}
	public Article getArticle() {
		return article;
	}
	public void setArticle(Article article) {
		this.article = article;
	}
	public List<Iine> getIineUsers() {
		return iineUsers;
	}
	public void setIineUsers(List<Iine> iineUsers) {
		this.iineUsers = iineUsers;
	}
}
