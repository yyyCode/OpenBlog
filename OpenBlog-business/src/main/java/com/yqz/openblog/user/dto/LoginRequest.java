package com.yqz.openblog.user.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    @NotBlank
    private String account; // username or email

    @NotBlank
    private String password;

    /**
     * 滑动验证通过后得到的 challengeId（{@code openblog.auth-security.slider.enabled=false} 时可不传）。
     */
    private String sliderChallengeId;

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSliderChallengeId() {
        return sliderChallengeId;
    }

    public void setSliderChallengeId(String sliderChallengeId) {
        this.sliderChallengeId = sliderChallengeId;
    }
}

