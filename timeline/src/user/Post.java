package user;

import java.io.Serializable;
import java.util.Calendar;

public class Post implements Serializable {
    private String username;
    private int id;
    private Calendar date;
    private String content;

    public Post(String username, int id, Calendar date, String content) {
        this.username = username;
        this.id = id;
        this.date = date;
        this.content = content;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Calendar getDate() {
        return date;
    }

    public void setDate(Calendar date) {
        this.date = date;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
