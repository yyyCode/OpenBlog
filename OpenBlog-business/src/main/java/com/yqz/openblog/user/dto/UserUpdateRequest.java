package com.yqz.openblog.user.dto;

import com.yqz.openblog.user.validation.AllowedMailbox;
import com.yqz.openblog.user.validation.AllowedUsername;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UserUpdateRequest {

    @Size(min = 3, max = 32)
    @Pattern(regexp = AllowedUsername.REGEXP, message = AllowedUsername.MESSAGE)
    private String username;

    @Size(max = 512)
    private String bio;

    @Size(max = 512)
    @Pattern(regexp = "^$|^(https?://[^\\s]+|/api/v1/media/[^\\s]+)$", message = "头像地址需为 http(s) 链接或本站媒体地址")
    private String avatarUrl;

    @Size(max = 64)
    @Pattern(regexp = AllowedMailbox.REGEXP_OPTIONAL, message = AllowedMailbox.MESSAGE)
    private String email;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}

