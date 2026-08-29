package com.yqz.openblog.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CommentCreateRequest {
    @NotBlank
    @Size(max = 2000)
    private String content;

    /**
     * 客户端生成的幂等 ID（前端每次提交生成 UUID，作为幂等 Key 的一部分）。
     */
    @Size(max = 64)
    private String requestId;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}

