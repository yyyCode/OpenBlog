package com.yqz.openblog.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FeedbackCreateRequest {

    @NotBlank
    @Size(max = 50)
    private String submitterName;

    @NotBlank
    @Size(max = 2000)
    private String content;

    public String getSubmitterName() {
        return submitterName;
    }

    public void setSubmitterName(String submitterName) {
        this.submitterName = submitterName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

