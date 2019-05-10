package utils;

import user.Post;

import java.io.Serializable;
import java.util.List;

public class Msg implements Serializable {
    private String type;
    private String password;
    private String ip;
    private List<Post> posts;
    private int lastPostId;
    private String superuser;
    private String superuserIp;
    private boolean status;
    private String followee;


    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public List<Post> getPosts() {
        return posts;
    }

    public void setPosts(List<Post> posts) {
        this.posts = posts;
    }

    public int getLastPostId() {
        return lastPostId;
    }

    public void setLastPostId(int lastPostId) {
        this.lastPostId = lastPostId;
    }

    public String getSuperuser() {
        return superuser;
    }

    public void setSuperuser(String superuser) {
        this.superuser = superuser;
    }

    public String getSuperuserIp() {
        return superuserIp;
    }

    public void setSuperuserIp(String superuserIp) {
        this.superuserIp = superuserIp;
    }

    public boolean getStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public String getFollowee() {
        return followee;
    }

    public void setFollowee(String followee) {
        this.followee = followee;
    }
}
