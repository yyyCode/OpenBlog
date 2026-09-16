package com.yqz.openblog.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 反馈提交请求。
 * 提交人身份由服务端从 JWT 解析（见 FeedbackController），请求体不再携带姓名，避免伪造。
 */
public class FeedbackCreateRequest {

    @NotBlank
    @Size(max = 2000)
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
