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
