package vn.edu.ut.udm08.shared.dto;

import vn.edu.ut.udm08.shared.model.User;

public class AuthUserDto {
    private Long id;
    private String username;
    private String phoneNumber;
    private String email;
    private String displayName;
    private String avatarType;
    private String avatarPath;

    public AuthUserDto() {}

    public AuthUserDto(User user) {
        if (user != null) {
            this.id = user.getId();
            this.username = user.getUsername();
            this.phoneNumber = user.getPhoneNumber();
            this.email = user.getEmail();
            this.displayName = user.getUsername();
            this.avatarType = user.getAvatarType();
            this.avatarPath = user.getAvatarPath();
        }
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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
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
}
