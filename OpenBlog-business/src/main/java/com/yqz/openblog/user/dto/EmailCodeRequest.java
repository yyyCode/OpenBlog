package com.yqz.openblog.user.dto;

import com.yqz.openblog.user.validation.AllowedMailbox;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 发送邮箱验证码请求。
 * purpose=register（默认）：邮箱需未注册（注册流程）；purpose=reset：邮箱需已注册（找回/修改密码流程）。
 */
public class EmailCodeRequest {

    @NotBlank
    @Email
    @Size(max = 64)
    @Pattern(regexp = AllowedMailbox.REGEXP, message = AllowedMailbox.MESSAGE)
    private String email;

    private String purpose;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }
}
