package com.yqz.openblog.project.dto;

import com.yqz.openblog.project.entity.ProjectStatus;
import java.time.Instant;

public class ProjectListItemResponse {
    private Long id;
    private String title;
    private String summary;
    private String coverMediaKey;
    private String techStack;
    private String projectUrl;
    private String githubUrl;
    private Integer sortOrder;
    private ProjectStatus status;
    private Instant publishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getCoverMediaKey() { return coverMediaKey; }
    public void setCoverMediaKey(String coverMediaKey) { this.coverMediaKey = coverMediaKey; }

    public String getTechStack() { return techStack; }
    public void setTechStack(String techStack) { this.techStack = techStack; }

    public String getProjectUrl() { return projectUrl; }
    public void setProjectUrl(String projectUrl) { this.projectUrl = projectUrl; }

    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
