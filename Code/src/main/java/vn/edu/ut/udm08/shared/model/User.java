package vn.edu.ut.udm08.shared.model;
import java.time.LocalDate;
import java.time.LocalDateTime;
public class User {
    private Long id;
    private String username;
    private String phoneNumber;
    private String email;
    private String passwordHash;
    private String avatarType;
    private String avatarPath;
    private String gender;
    private LocalDate dob;
    private String bio;
    private String coverUrl;
    private LocalDateTime createdAt;
    public User() {}
    public User(Long id, String username, String phoneNumber, String email, String passwordHash, String avatarType, String avatarPath, String gender, LocalDate dob, String bio, String coverUrl, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.passwordHash = passwordHash;
        this.avatarType = avatarType;
        this.avatarPath = avatarPath;
        this.gender = gender;
        this.dob = dob;
        this.bio = bio;
        this.coverUrl = coverUrl;
        this.createdAt = createdAt;
    }
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getPhoneNumber() {
        return phoneNumber;
    }
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public String getPasswordHash() {
        return passwordHash;
    }
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
    public String getAvatarType() {
        return avatarType;
    }
    public void setAvatarType(String avatarType) {
        this.avatarType = avatarType;
    }
    public String getAvatarPath() {
        return avatarPath;
    }
    public void setAvatarPath(String avatarPath) {
        this.avatarPath = avatarPath;
    }
    public String getGender() {
        return gender;
    }
    public void setGender(String gender) {
        this.gender = gender;
    }
    public LocalDate getDob() {
        return dob;
    }
    public void setDob(LocalDate dob) {
        this.dob = dob;
    }
    public String getBio() {
        return bio;
    }
    public void setBio(String bio) {
        this.bio = bio;
    }
    public String getCoverUrl() {
        return coverUrl;
    }
    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
