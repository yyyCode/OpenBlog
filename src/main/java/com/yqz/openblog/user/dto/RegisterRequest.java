package com.yqz.openblog.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 32)
    private String username;

    @NotBlank
    @Email
    @Size(max = 64)
    private String email;

    @NotBlank
    @Size(min = 6, max = 72)
    private String password;

    @Size(max = 32)
    private String nickname;

    private String sliderChallengeId;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getSliderChallengeId() {
        return sliderChallengeId;
    }

    public void setSliderChallengeId(String sliderChallengeId) {
        this.sliderChallengeId = sliderChallengeId;
    }
}

