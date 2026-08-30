package com.yqz.openblog.user.dto;

import com.yqz.openblog.user.validation.AllowedMailbox;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ChangePasswordRequest {

    @NotBlank
    @Email
    @Size(max = 64)
    @Pattern(regexp = AllowedMailbox.REGEXP, message = AllowedMailbox.MESSAGE)
    private String email;

    /** 发到该邮箱的找回密码验证码（6 位数字，必须持有邮箱才能改密）。 */
    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "验证码格式不正确")
    private String code;

    @NotBlank
    @Size(min = 6, max = 72)
    private String newPassword;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
