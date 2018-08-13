package hidakkathon2018.model;

import java.io.*;
import java.util.*;

public class Tag implements Serializable {
	private static final long serialVersionUID = -3913066638602995311L;

	private Integer id;
	private String tagname;
	private Date createdAt;

	private Integer tagCount;

	public Integer getId() {
		return id;
	}
	public void setId(Integer id) {
		this.id = id;
	}
	public String getTagname() {
		return tagname;
	}
	public void setTagname(String tagname) {
		this.tagname = tagname;
	}
	public Date getCreatedAt() {
		return createdAt;
	}
	public void setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
	}
	public Integer getTagCount() {
		return tagCount;
	}
	public void setTagCount(Integer tagCount) {
		this.tagCount = tagCount;
	}
}
