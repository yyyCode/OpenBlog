# Project Recommendation Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "项目推荐" (Project Recommendations) section to the blog with independent database table, backend API, frontend pages, and admin management.

**Architecture:** New `projects` database table + JPA entity + Service/Controller layer following the changelog pattern. Frontend adds list/detail views for visitors and a management view in the admin console. The top nav bar gets a new link with increased font size and spacing.

**Tech Stack:** Java 21, Spring Boot 3 / JPA, MyBatis-Plus (just for entity, using JPA repo), Vue 3 + Vue Router, CSS custom properties

---

### Task 1: Create projects database table SQL

**Files:**
- Create: `OpenBlog-business/src/main/resources/sql/projects.sql`

- [ ] **Step 1: Write the SQL file**

```sql
-- projects.sql
CREATE TABLE IF NOT EXISTS projects (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(120) NOT NULL COMMENT '项目名称',
    summary         VARCHAR(255)  COMMENT '项目简介',
    content_markdown TEXT         COMMENT 'Markdown 正文',
    cover_media_key VARCHAR(64)   COMMENT '封面图 media key',
    tech_stack      VARCHAR(255)  COMMENT '技术栈（逗号分隔）',
    project_url     VARCHAR(512)  COMMENT '项目链接',
    github_url      VARCHAR(512)  COMMENT '源码链接',
    sort_order      INT DEFAULT 0 COMMENT '排序值（越小越靠前）',
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED',
    published_at    DATETIME      COMMENT '发布时间',
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL,
    INDEX idx_projects_status_sort (status, sort_order, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: Commit**

```bash
git add OpenBlog-business/src/main/resources/sql/projects.sql
git commit -m "feat(project): add projects table DDL"
```

---

### Task 2: Create ProjectStatus enum

**Files:**
- Create: `OpenBlog-business/src/main/java/com/yqz/openblog/project/entity/ProjectStatus.java`

- [ ] **Step 1: Write the enum**

```java
package com.yqz.openblog.project.entity;

public enum ProjectStatus {
    DRAFT,
    PUBLISHED
}
```

- [ ] **Step 2: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/project/entity/ProjectStatus.java
git commit -m "feat(project): add ProjectStatus enum"
```

---

### Task 3: Create Project entity

**Files:**
- Create: `OpenBlog-business/src/main/java/com/yqz/openblog/project/entity/Project.java`

- [ ] **Step 1: Write the JPA entity**

```java
package com.yqz.openblog.project.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "projects", indexes = {
        @Index(name = "idx_projects_status_sort", columnList = "status,sort_order,published_at")
})
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 255)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String contentMarkdown;

    @Column(length = 64)
    private String coverMediaKey;

    @Column(length = 255)
    private String techStack;

    @Column(length = 512)
    private String projectUrl;

    @Column(length = 512)
    private String githubUrl;

    @Column(nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProjectStatus status;

    @Column
    private Instant publishedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (sortOrder == null) sortOrder = 0;
        if (status == null) status = ProjectStatus.DRAFT;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Project() {}

    // --- getters / setters ---

    public Long getId() { return id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getContentMarkdown() { return contentMarkdown; }
    public void setContentMarkdown(String contentMarkdown) { this.contentMarkdown = contentMarkdown; }

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

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 2: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/project/entity/Project.java
git commit -m "feat(project): add Project JPA entity"
```

---

### Task 4: Create ProjectRepository

**Files:**
- Create: `OpenBlog-business/src/main/java/com/yqz/openblog/project/repo/ProjectRepository.java`

- [ ] **Step 1: Write the JPA repository**

```java
package com.yqz.openblog.project.repo;

import com.yqz.openblog.project.entity.Project;
import com.yqz.openblog.project.entity.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Page<Project> findByStatusOrderBySortOrderAscPublishedAtDesc(ProjectStatus status, Pageable pageable);
}
```

- [ ] **Step 2: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/project/repo/ProjectRepository.java
git commit -m "feat(project): add ProjectRepository"
```

---

### Task 5: Create DTOs

**Files:**
- Create: `OpenBlog-business/src/main/java/com/yqz/openblog/project/dto/ProjectListItemResponse.java`
- Create: `OpenBlog-business/src/main/java/com/yqz/openblog/project/dto/ProjectDetailResponse.java`
- Create: `OpenBlog-business/src/main/java/com/yqz/openblog/project/dto/ProjectUpsertRequest.java`

- [ ] **Step 1: Write ProjectListItemResponse**

```java
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
```

- [ ] **Step 2: Write ProjectDetailResponse**

```java
package com.yqz.openblog.project.dto;

import com.yqz.openblog.project.entity.ProjectStatus;
import java.time.Instant;

public class ProjectDetailResponse {
    private Long id;
    private String title;
    private String summary;
    private String contentMarkdown;
    private String coverMediaKey;
    private String techStack;
    private String projectUrl;
    private String githubUrl;
    private Integer sortOrder;
    private ProjectStatus status;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getContentMarkdown() { return contentMarkdown; }
    public void setContentMarkdown(String contentMarkdown) { this.contentMarkdown = contentMarkdown; }

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

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 3: Write ProjectUpsertRequest**

```java
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
```

- [ ] **Step 4: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/project/dto/
git commit -m "feat(project): add project DTOs"
```

---

### Task 6: Create ProjectService

**Files:**
- Create: `OpenBlog-business/src/main/java/com/yqz/openblog/project/service/ProjectService.java`

- [ ] **Step 1: Write the service**

```java
package com.yqz.openblog.project.service;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.project.dto.ProjectDetailResponse;
import com.yqz.openblog.project.dto.ProjectListItemResponse;
import com.yqz.openblog.project.dto.ProjectUpsertRequest;
import com.yqz.openblog.project.entity.Project;
import com.yqz.openblog.project.entity.ProjectStatus;
import com.yqz.openblog.project.repo.ProjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public PageResult<ProjectListItemResponse> listPublished(int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        Page<Project> pg = projectRepository.findByStatusOrderBySortOrderAscPublishedAtDesc(
                ProjectStatus.PUBLISHED, PageRequest.of(p, s));
        List<ProjectListItemResponse> items = pg.getContent().stream().map(this::toListItem).toList();
        return new PageResult<>(items, pg.getNumber(), pg.getSize(), pg.getTotalElements());
    }

    public ProjectDetailResponse detail(Long id) {
        Project p = projectRepository.findById(id)
                .orElseThrow(() -> new BizException(4041, "项目不存在"));
        return toDetail(p);
    }

    public PageResult<ProjectListItemResponse> listAll(int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        Page<Project> pg = projectRepository.findAll(PageRequest.of(p, s));
        List<ProjectListItemResponse> items = pg.getContent().stream().map(this::toListItem).toList();
        return new PageResult<>(items, pg.getNumber(), pg.getSize(), pg.getTotalElements());
    }

    @Transactional
    public ProjectListItemResponse create(ProjectUpsertRequest req) {
        Project p = new Project();
        apply(p, req);
        p = projectRepository.save(p);
        return toListItem(p);
    }

    @Transactional
    public ProjectListItemResponse update(Long id, ProjectUpsertRequest req) {
        Project p = projectRepository.findById(id)
                .orElseThrow(() -> new BizException(4041, "项目不存在"));
        apply(p, req);
        p = projectRepository.save(p);
        return toListItem(p);
    }

    @Transactional
    public void delete(Long id) {
        if (!projectRepository.existsById(id)) {
            throw new BizException(4041, "项目不存在");
        }
        projectRepository.deleteById(id);
    }

    private void apply(Project p, ProjectUpsertRequest req) {
        p.setTitle(req.getTitle().trim());
        p.setSummary(req.getSummary() != null ? req.getSummary().trim() : null);
        p.setContentMarkdown(req.getContentMarkdown());
        p.setCoverMediaKey(req.getCoverMediaKey() != null ? req.getCoverMediaKey().trim() : null);
        p.setTechStack(req.getTechStack() != null ? req.getTechStack().trim() : null);
        p.setProjectUrl(req.getProjectUrl() != null ? req.getProjectUrl().trim() : null);
        p.setGithubUrl(req.getGithubUrl() != null ? req.getGithubUrl().trim() : null);
        p.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        if (req.getStatus() != null) {
            p.setStatus(ProjectStatus.valueOf(req.getStatus().toUpperCase()));
        }
        if (req.getPublishedAt() != null && !req.getPublishedAt().isBlank()) {
            p.setPublishedAt(Instant.parse(req.getPublishedAt()));
        } else if (p.getId() == null && p.getStatus() == ProjectStatus.PUBLISHED) {
            p.setPublishedAt(Instant.now());
        }
    }

    private ProjectListItemResponse toListItem(Project p) {
        ProjectListItemResponse r = new ProjectListItemResponse();
        r.setId(p.getId());
        r.setTitle(p.getTitle());
        r.setSummary(p.getSummary());
        r.setCoverMediaKey(p.getCoverMediaKey());
        r.setTechStack(p.getTechStack());
        r.setProjectUrl(p.getProjectUrl());
        r.setGithubUrl(p.getGithubUrl());
        r.setSortOrder(p.getSortOrder());
        r.setStatus(p.getStatus());
        r.setPublishedAt(p.getPublishedAt());
        return r;
    }

    private ProjectDetailResponse toDetail(Project p) {
        ProjectDetailResponse r = new ProjectDetailResponse();
        r.setId(p.getId());
        r.setTitle(p.getTitle());
        r.setSummary(p.getSummary());
        r.setContentMarkdown(p.getContentMarkdown());
        r.setCoverMediaKey(p.getCoverMediaKey());
        r.setTechStack(p.getTechStack());
        r.setProjectUrl(p.getProjectUrl());
        r.setGithubUrl(p.getGithubUrl());
        r.setSortOrder(p.getSortOrder());
        r.setStatus(p.getStatus());
        r.setPublishedAt(p.getPublishedAt());
        r.setCreatedAt(p.getCreatedAt());
        r.setUpdatedAt(p.getUpdatedAt());
        return r;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/project/service/ProjectService.java
git commit -m "feat(project): add ProjectService"
```

---

### Task 7: Create ProjectController

**Files:**
- Create: `OpenBlog-business/src/main/java/com/yqz/openblog/project/controller/ProjectController.java`

- [ ] **Step 1: Write the controller**

```java
package com.yqz.openblog.project.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.project.dto.ProjectDetailResponse;
import com.yqz.openblog.project.dto.ProjectListItemResponse;
import com.yqz.openblog.project.dto.ProjectUpsertRequest;
import com.yqz.openblog.project.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
@CrossOrigin(origins = "*")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ApiResponse<PageResult<ProjectListItemResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(projectService.listPublished(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(projectService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProjectListItemResponse> create(@RequestBody @Valid ProjectUpsertRequest req) {
        return ApiResponse.ok(projectService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProjectListItemResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid ProjectUpsertRequest req) {
        return ApiResponse.ok(projectService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ApiResponse.ok();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/project/controller/ProjectController.java
git commit -m "feat(project): add ProjectController (public + admin API)"
```

---

### Task 8: Create frontend API layer

**Files:**
- Create: `vue/src/api/project.js`

- [ ] **Step 1: Write the API module**

```javascript
import { request } from './http'

export function fetchProjects(page = 0, size = 20) {
  return request(`/api/v1/projects?page=${page}&size=${size}`)
}

export function fetchProjectDetail(id) {
  return request(`/api/v1/projects/${id}`)
}

export function createProject(payload) {
  return request('/api/v1/projects', {
    method: 'POST',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}

export function updateProject(id, payload) {
  return request(`/api/v1/projects/${id}`, {
    method: 'PUT',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}

export function deleteProject(id) {
  return request(`/api/v1/projects/${id}`, {
    method: 'DELETE',
    withAuth: true
  })
}

export function coverUrl(key) {
  if (!key) return ''
  const { API_BASE } = require('./http')
  return `${API_BASE}/api/v1/media/files/${key}`
}
```

- [ ] **Step 2: Commit**

```bash
git add vue/src/api/project.js
git commit -m "feat(project): add frontend API layer"
```

---

### Task 9: Create Project List View

**Files:**
- Create: `vue/src/views/ProjectsListView.vue`

- [ ] **Step 1: Write the project list page**

```vue
<template>
  <div class="blog-container">
    <div v-if="loading" class="card">
      <div class="card-body" style="padding: 22px; color: var(--muted)">加载中...</div>
    </div>

    <div v-else>
      <div class="projects-page-head">
        <h1 class="projects-page-title">项目推荐</h1>
        <p class="projects-page-sub">一些我参与或独立完成的项目</p>
      </div>

      <div v-if="items.length === 0" class="card">
        <div class="card-body" style="padding: 22px; color: var(--muted)">暂无项目</div>
      </div>

      <div v-else class="projects-grid">
        <div
          v-for="item in items"
          :key="item.id"
          class="project-card"
          @click="go(item.id)"
        >
          <div class="project-card-cover">
            <img v-if="item.coverMediaKey" :src="coverUrl(item.coverMediaKey)" alt="cover" />
          </div>
          <div class="project-card-body">
            <h2 class="project-card-title">{{ item.title }}</h2>
            <p class="project-card-summary">{{ item.summary || '' }}</p>
            <div v-if="item.techStack" class="project-card-tags">
              <span
                v-for="tag in splitTags(item.techStack)"
                :key="tag"
                class="project-tag"
              >{{ tag }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchProjects, coverUrl } from '../api/project'

const router = useRouter()
const loading = ref(true)
const items = ref([])

onMounted(async () => {
  loading.value = true
  try {
    const res = await fetchProjects(0, 50)
    items.value = res?.items || []
  } finally {
    loading.value = false
  }
})

function go(id) {
  router.push(`/project/${id}`)
}

function splitTags(v) {
  if (!v) return []
  return v.split(',').map(s => s.trim()).filter(Boolean)
}
</script>

<style scoped>
.projects-page-head {
  margin-bottom: 32px;
}

.projects-page-title {
  margin: 0;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--text);
}

.projects-page-sub {
  margin: 8px 0 0;
  font-size: 15px;
  color: var(--muted);
}

.projects-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
}

@media (max-width: 1024px) {
  .projects-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .projects-grid {
    grid-template-columns: 1fr;
  }
}

.project-card {
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--border);
  background: var(--card);
  cursor: pointer;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.project-card:hover {
  border-color: var(--accent);
  box-shadow: 0 4px 16px rgba(51, 112, 255, 0.1);
}

.project-card-cover {
  aspect-ratio: 16 / 9;
  background: var(--placeholder-tint);
}

.project-card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.project-card-body {
  padding: 14px 14px 16px;
}

.project-card-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--text);
  line-height: 1.3;
}

.project-card-summary {
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.project-card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.project-tag {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--accent);
  background: rgba(51, 112, 255, 0.08);
}

[data-theme='dark'] .project-tag {
  background: rgba(74, 127, 255, 0.15);
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add vue/src/views/ProjectsListView.vue
git commit -m "feat(project): add project list view"
```

---

### Task 10: Create Project Detail View

**Files:**
- Create: `vue/src/views/ProjectDetailView.vue`

- [ ] **Step 1: Write the project detail page**

```vue
<template>
  <div class="blog-container">
    <div v-if="loading" class="card">
      <div class="card-body" style="padding: 22px">加载中...</div>
    </div>

    <div v-else>
      <div v-if="!project?.id" class="card">
        <div class="card-body" style="padding: 22px; color: var(--muted)">项目不存在</div>
      </div>

      <div v-else>
        <router-link to="/projects" class="article-back">
          <span class="article-back-chev">←</span>
          返回项目列表
        </router-link>

        <h1 class="project-detail-title">{{ project.title }}</h1>

        <div class="project-detail-meta">
          <span>{{ formatDate(project.publishedAt) }}</span>
          <template v-if="project.techStack">
            <span class="article-kicker-dot">·</span>
            <div class="project-detail-tags">
              <span
                v-for="tag in splitTags(project.techStack)"
                :key="tag"
                class="project-detail-tag"
              >{{ tag }}</span>
            </div>
          </template>
        </div>

        <figure v-if="project.coverMediaKey" class="project-detail-cover">
          <img :src="coverUrl(project.coverMediaKey)" alt="" />
        </figure>

        <div class="markdown-body article-markdown" v-html="html" />

        <div v-if="project.projectUrl || project.githubUrl" class="project-detail-links">
          <a
            v-if="project.projectUrl"
            :href="project.projectUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="project-link-btn"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
              <polyline points="15 3 21 3 21 9" />
              <line x1="10" y1="14" x2="21" y2="3" />
            </svg>
            查看项目
          </a>
          <a
            v-if="project.githubUrl"
            :href="project.githubUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="project-link-btn project-link-btn--gh"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 .5C5.73.5.75 5.67.75 12.09c0 5.15 3.28 9.52 7.83 11.06.57.11.78-.25.78-.55 0-.27-.01-1.17-.02-2.12-3.18.71-3.85-1.39-3.85-1.39-.52-1.36-1.28-1.72-1.28-1.72-1.05-.74.08-.73.08-.73 1.16.08 1.77 1.23 1.77 1.23 1.03 1.8 2.7 1.28 3.36.98.1-.77.4-1.28.73-1.57-2.54-.3-5.21-1.3-5.21-5.79 0-1.28.44-2.33 1.16-3.15-.12-.29-.5-1.48.11-3.08 0 0 .95-.31 3.11 1.2.9-.26 1.86-.39 2.82-.39.96 0 1.92.13 2.82.39 2.16-1.51 3.11-1.2 3.11-1.2.61 1.6.23 2.79.11 3.08.72.82 1.16 1.87 1.16 3.15 0 4.5-2.68 5.49-5.23 5.79.41.36.78 1.07.78 2.17 0 1.57-.02 2.83-.02 3.22 0 .3.21.67.79.55 4.55-1.54 7.83-5.91 7.83-11.06C23.25 5.67 18.27.5 12 .5z" />
            </svg>
            GitHub
          </a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { fetchProjectDetail, coverUrl } from '../api/project'

const route = useRoute()
const loading = ref(true)
const project = ref({})

async function load() {
  loading.value = true
  try {
    const id = route.params.id
    project.value = await fetchProjectDetail(id)
  } catch {
    project.value = {}
  } finally {
    loading.value = false
  }
}

onMounted(() => load())

watch(
  () => route.params.id,
  () => load()
)

const html = computed(() => {
  const md = project.value?.contentMarkdown || ''
  const raw = marked.parse(md)
  return DOMPurify.sanitize(raw)
})

function formatDate(v) {
  if (!v) return ''
  try {
    return new Date(v).toISOString().slice(0, 10)
  } catch {
    return ''
  }
}

function splitTags(v) {
  if (!v) return []
  return v.split(',').map(s => s.trim()).filter(Boolean)
}
</script>

<style scoped>
.project-detail-title {
  margin: 0 0 12px;
  font-size: clamp(28px, 4.2vw, 36px);
  font-weight: 700;
  letter-spacing: -0.025em;
  line-height: 1.15;
  color: var(--text);
}

.project-detail-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 28px;
  font-size: 13px;
  color: var(--muted);
}

.project-detail-tags {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
}

.project-detail-tag {
  display: inline-block;
  padding: 2px 7px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--accent);
  background: rgba(51, 112, 255, 0.08);
}

[data-theme='dark'] .project-detail-tag {
  background: rgba(74, 127, 255, 0.15);
}

.project-detail-cover {
  margin: 0 0 36px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--placeholder-tint);
}

.project-detail-cover img {
  width: 100%;
  display: block;
  vertical-align: middle;
  object-fit: cover;
  max-height: min(52vh, 440px);
}

.project-detail-links {
  display: flex;
  gap: 12px;
  margin-top: 40px;
}

.project-link-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  color: #fff;
  background: var(--accent);
  border: 1px solid transparent;
  transition: background 0.15s ease;
}

.project-link-btn:hover {
  background: var(--accent2);
}

.project-link-btn--gh {
  background: #24292e;
}

.project-link-btn--gh:hover {
  background: #1b1f23;
}

[data-theme='dark'] .project-link-btn--gh {
  background: #3a3a3e;
}

[data-theme='dark'] .project-link-btn--gh:hover {
  background: #4a4a50;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add vue/src/views/ProjectDetailView.vue
git commit -m "feat(project): add project detail view"
```

---

### Task 11: Create Console Project Management View

**Files:**
- Create: `vue/src/views/ConsoleProjectsView.vue`

- [ ] **Step 1: Write the admin management page**

```vue
<template>
  <div class="console-page">
    <div class="console-page-header">
      <div class="console-page-title">
        <span class="console-page-title-ico">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="2" />
            <path d="M9 3v18" />
            <path d="M3 9h18" />
          </svg>
        </span>
        <h1>项目管理</h1>
      </div>
      <button type="button" class="console-btn-dark" @click="showEditor = true; editing = null">
        ＋ 新建项目
      </button>
    </div>

    <!-- 编辑器弹出层 -->
    <div v-if="showEditor" class="console-editor-overlay" @click.self="closeEditor">
      <div class="console-editor-panel">
        <h2 class="console-editor-title">{{ editing ? '编辑项目' : '新建项目' }}</h2>

        <div class="console-editor-form">
          <div class="field">
            <div class="label">项目名称 *</div>
            <input v-model="form.title" class="input" placeholder="项目名称" maxlength="120" />
          </div>
          <div class="field">
            <div class="label">简介</div>
            <input v-model="form.summary" class="input" placeholder="一句话简介" maxlength="255" />
          </div>
          <div class="field">
            <div class="label">封面图 Media Key</div>
            <input v-model="form.coverMediaKey" class="input" placeholder="留空则不显示封面" />
          </div>
          <div class="field">
            <div class="label">技术栈</div>
            <input v-model="form.techStack" class="input" placeholder="Vue, Spring Boot, MySQL（逗号分隔）" />
          </div>
          <div class="field">
            <div class="label">项目链接</div>
            <input v-model="form.projectUrl" class="input" placeholder="https://..." />
          </div>
          <div class="field">
            <div class="label">GitHub 链接</div>
            <input v-model="form.githubUrl" class="input" placeholder="https://github.com/..." />
          </div>
          <div class="field">
            <div class="label">排序值</div>
            <input v-model.number="form.sortOrder" class="input" type="number" placeholder="0" />
          </div>
          <div class="field">
            <div class="label">状态</div>
            <select v-model="form.status" class="input">
              <option value="DRAFT">草稿</option>
              <option value="PUBLISHED">已发布</option>
            </select>
          </div>
          <div class="field">
            <div class="label">正文 (Markdown) *</div>
            <textarea v-model="form.contentMarkdown" class="textarea" rows="12" placeholder="支持 Markdown 语法"></textarea>
          </div>
        </div>

        <div class="console-editor-actions">
          <button type="button" class="btn" @click="closeEditor">取消</button>
          <button type="button" class="btn primary" :disabled="saving" @click="save">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 列表 -->
    <div v-if="loading" class="card">
      <div class="card-body" style="padding: 22px">加载中...</div>
    </div>

    <div v-else>
      <div v-if="items.length === 0" class="card">
        <div class="card-body" style="padding: 22px; color: var(--muted)">暂无项目</div>
      </div>

      <div v-else class="card">
        <div class="admin-article-table">
          <div
            v-for="item in items"
            :key="item.id"
            class="admin-article-row"
          >
            <div class="admin-article-info">
              <div class="admin-article-title">{{ item.title }}</div>
              <div class="admin-article-meta">
                <span :class="statusClass(item.status)">{{ item.status }}</span>
                <span>排序: {{ item.sortOrder ?? 0 }}</span>
                <span v-if="item.publishedAt">{{ formatDate(item.publishedAt) }}</span>
              </div>
            </div>
            <div class="admin-article-actions">
              <button type="button" class="btn btn-sm" @click="editItem(item)">编辑</button>
              <button type="button" class="btn btn-sm btn-outline-danger" @click="remove(item.id)">删除</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { fetchProjects, createProject, updateProject, deleteProject } from '../api/project'
import { showMessage } from '../utils/message'

const loading = ref(true)
const items = ref([])
const showEditor = ref(false)
const editing = ref(null)
const saving = ref(false)
const form = ref(emptyForm())

function emptyForm() {
  return {
    title: '',
    summary: '',
    contentMarkdown: '',
    coverMediaKey: '',
    techStack: '',
    projectUrl: '',
    githubUrl: '',
    sortOrder: 0,
    status: 'DRAFT'
  }
}

async function load() {
  loading.value = true
  try {
    const res = await fetchProjects(0, 100)
    items.value = res?.items || []
  } finally {
    loading.value = false
  }
}

onMounted(() => load())

function editItem(item) {
  editing.value = item
  form.value = {
    title: item.title || '',
    summary: item.summary || '',
    contentMarkdown: '',  // detail 会返回 contentMarkdown
    coverMediaKey: item.coverMediaKey || '',
    techStack: item.techStack || '',
    projectUrl: item.projectUrl || '',
    githubUrl: item.githubUrl || '',
    sortOrder: item.sortOrder ?? 0,
    status: item.status || 'DRAFT'
  }
  showEditor.value = true
  // 加载完整详情获取 contentMarkdown
  import('../api/project').then(m => m.fetchProjectDetail(item.id)).then(d => {
    if (d?.contentMarkdown) form.value.contentMarkdown = d.contentMarkdown
  }).catch(() => {})
}

function closeEditor() {
  showEditor.value = false
  editing.value = null
  form.value = emptyForm()
}

async function save() {
  if (!form.value.title.trim()) {
    showMessage('请输入项目名称')
    return
  }
  if (!form.value.contentMarkdown.trim()) {
    showMessage('请输入正文内容')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateProject(editing.value.id, form.value)
      showMessage('更新成功')
    } else {
      await createProject(form.value)
      showMessage('创建成功')
    }
    closeEditor()
    await load()
  } catch {
    showMessage('保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(id) {
  if (!confirm('确定删除此项目？')) return
  try {
    await deleteProject(id)
    showMessage('已删除')
    await load()
  } catch {
    showMessage('删除失败')
  }
}

function statusClass(s) {
  return s === 'PUBLISHED' ? 'tag-status-active' : 'tag-status-pending'
}

function formatDate(v) {
  if (!v) return ''
  try {
    return new Date(v).toISOString().slice(0, 10)
  } catch {
    return ''
  }
}
</script>

<style scoped>
.admin-article-table {
  display: flex;
  flex-direction: column;
}

.admin-article-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--console-border);
}

.admin-article-row:last-child {
  border-bottom: none;
}

.admin-article-info {
  flex: 1;
  min-width: 0;
}

.admin-article-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--console-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-article-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--console-muted);
}

.admin-article-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* Editor overlay */
.console-editor-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 20000;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 40px 16px;
  overflow-y: auto;
}

.console-editor-panel {
  background: var(--card);
  border-radius: 12px;
  width: 100%;
  max-width: 720px;
  padding: 28px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.2);
}

.console-editor-title {
  margin: 0 0 20px;
  font-size: 18px;
  font-weight: 700;
  color: var(--text);
}

.console-editor-form {
  max-height: 60vh;
  overflow-y: auto;
}

.console-editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add vue/src/views/ConsoleProjectsView.vue
git commit -m "feat(project): add console project management view"
```

---

### Task 12: Update router with project routes

**Files:**
- Modify: `vue/src/router/index.js`

- [ ] **Step 1: Add imports and routes**

Add imports at the top (after the existing imports, around line 13):

```js
import ProjectsListView from '../views/ProjectsListView.vue'
import ProjectDetailView from '../views/ProjectDetailView.vue'
import ConsoleProjectsView from '../views/ConsoleProjectsView.vue'
```

Add public routes after the `'/search'` block (around line 51):

```js
  {
    path: '/projects',
    name: 'projectsList',
    component: ProjectsListView
  },
  {
    path: '/project/:id',
    name: 'projectDetail',
    component: ProjectDetailView,
    props: true
  },
```

Add console route after the `changelog` entry (around line 85):

```js
      { path: 'projects', name: 'consoleProjects', component: ConsoleProjectsView },
```

- [ ] **Step 2: Commit**

```bash
git add vue/src/router/index.js
git commit -m "feat(project): register project routes"
```

---

### Task 13: Update BlogHeader with project nav link and style tweaks

**Files:**
- Modify: `vue/src/components/BlogHeader.vue`
- Modify: `vue/src/assets/blog.css`

- [ ] **Step 1: Add project nav link in BlogHeader.vue**

After the "文章" router-link (line 35 in the current file) and before "反馈" (line 37), insert:

```html
        <router-link to="/projects" class="site-nav-link" active-class="active">
          <span class="site-nav-ico" aria-hidden="true">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2" />
              <path d="M9 3v18" />
              <path d="M3 9h18" />
            </svg>
          </span>
          项目推荐
        </router-link>
```

- [ ] **Step 2: Update nav font size and spacing in blog.css**

Find `.site-nav-link` (line 2012) and update:

```css
.site-nav-link {
  color: var(--muted);
  text-decoration: none;
  font-size: 15px;        /* was 14px */
  font-weight: 400;
  letter-spacing: 0.06em;
  background: transparent;
  border: none;
  padding: 6px 2px;
  cursor: pointer;
  appearance: none;
  -webkit-appearance: none;
  line-height: 1.2;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}
```

Find `.site-nav` (line 2006) and update:

```css
.site-nav {
  display: inline-flex;
  align-items: center;
  gap: 28px;              /* was 22px */
}
```

Find `.site-nav-ico` (line 2031) and update icon size:

```css
.site-nav-ico {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 17px;            /* was 16px */
  height: 17px;           /* was 16px */
  color: currentColor;
  flex-shrink: 0;
  opacity: 0.95;
}

.site-nav-ico svg {
  width: 17px;            /* was 16px */
  height: 17px;           /* was 16px */
}
```

Find the mobile breakpoint `(max-width: 520px)` around line 2416 and update:

```css
@media (max-width: 520px) {
  .site-nav {
    gap: 12px;
  }
  .site-nav-link-sm-hide {
    display: none;
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add vue/src/components/BlogHeader.vue vue/src/assets/blog.css
git commit -m "feat(project): add project nav link, increase nav font/spacing"
```

---

### Task 14: Add projects link to Console sidebar

**Files:**
- Modify: `vue/src/layouts/ConsoleLayout.vue`

- [ ] **Step 1: Insert projects nav item in content section**

After the "更新日志" router-link (around line 76), insert:

```html
          <router-link to="/console/projects" class="console-nav-item" active-class="active">
            <span class="console-nav-ico" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="3" width="18" height="18" rx="2" />
                <path d="M9 3v18" />
                <path d="M3 9h18" />
              </svg>
            </span>
            项目管理
          </router-link>
```

- [ ] **Step 2: Commit**

```bash
git add vue/src/layouts/ConsoleLayout.vue
git commit -m "feat(project): add project management to console sidebar"
```

---

## Self-Review Checklist

1. **Spec coverage**: Every requirement from the spec is covered:
   - Project DB table → Task 1
   - Project entity → Tasks 2-3
   - Backend repository → Task 4
   - Backend DTOs → Task 5
   - Backend service → Task 6
   - Backend controller → Task 7
   - Frontend API → Task 8
   - Project list page → Task 9
   - Project detail page → Task 10
   - Console management → Task 11
   - Routes → Task 12
   - Nav bar link + styling → Task 13
   - Console sidebar → Task 14

2. **Placeholder check**: No TBD, TODO, or vague steps. Every step has exact code.

3. **Type consistency**: All method names match across tasks (fetchProjects, fetchProjectDetail, etc.), service method names are consistent (listPublished, detail, create, update, delete).
