package com.yqz.openblog.user.dto;

import com.yqz.openblog.user.validation.AllowedMailbox;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 32)
    private String username;

    @NotBlank
    @Email
    @Size(max = 64)
    @Pattern(regexp = AllowedMailbox.REGEXP, message = AllowedMailbox.MESSAGE)
    private String email;

    @NotBlank
    @Size(min = 6, max = 72)
    private String password;

    /** 邮箱注册验证码（6 位数字）。 */
    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "验证码格式不正确")
    private String code;

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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getSliderChallengeId() {
        return sliderChallengeId;
    }

    public void setSliderChallengeId(String sliderChallengeId) {
        this.sliderChallengeId = sliderChallengeId;
    }
}

