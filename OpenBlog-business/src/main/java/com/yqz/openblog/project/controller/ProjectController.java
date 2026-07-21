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
