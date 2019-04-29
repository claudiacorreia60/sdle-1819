package utils;

import user.Post;

import java.util.List;

public class Msg {
    private String username;
    private String type;
    private String password;
    private String ip;
    private List<Post> posts;
    private int lastPostId;
    private List<String> followees;
    private String superuser;
    private String superuserIp;
    private boolean status;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

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

    public List<String> getFollowees() {
        return followees;
    }

    public void setFollowees(List<String> followees) {
        this.followees = followees;
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

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }
}
