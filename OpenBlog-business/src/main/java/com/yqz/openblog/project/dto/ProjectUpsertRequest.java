package com.yqz.openblog.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProjectUpsertRequest {

    @NotBlank
    @Size(max = 120)
    private String title;

    @Size(max = 255)
    private String summary;

    @NotBlank
    private String contentMarkdown;

    @Size(max = 64)
    private String coverMediaKey;

    @Size(max = 255)
    private String techStack;

    @Size(max = 512)
    private String projectUrl;

    @Size(max = 512)
    private String githubUrl;

    private Integer sortOrder;

    private String status;

    /** ISO-8601 可选 */
    private String publishedAt;

    public @NotBlank @Size(max = 120) String getTitle() { return title; }
    public void setTitle(@NotBlank @Size(max = 120) String title) { this.title = title; }

    public @Size(max = 255) String getSummary() { return summary; }
    public void setSummary(@Size(max = 255) String summary) { this.summary = summary; }

    public @NotBlank String getContentMarkdown() { return contentMarkdown; }
    public void setContentMarkdown(@NotBlank String contentMarkdown) { this.contentMarkdown = contentMarkdown; }

    public @Size(max = 64) String getCoverMediaKey() { return coverMediaKey; }
    public void setCoverMediaKey(@Size(max = 64) String coverMediaKey) { this.coverMediaKey = coverMediaKey; }

    public @Size(max = 255) String getTechStack() { return techStack; }
    public void setTechStack(@Size(max = 255) String techStack) { this.techStack = techStack; }

    public @Size(max = 512) String getProjectUrl() { return projectUrl; }
    public void setProjectUrl(@Size(max = 512) String projectUrl) { this.projectUrl = projectUrl; }

    public @Size(max = 512) String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(@Size(max = 512) String githubUrl) { this.githubUrl = githubUrl; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPublishedAt() { return publishedAt; }
    public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }
}
