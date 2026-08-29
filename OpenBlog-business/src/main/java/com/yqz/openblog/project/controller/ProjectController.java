package com.yqz.openblog.project.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.project.dto.ProjectDetailResponse;
import com.yqz.openblog.project.dto.ProjectListItemResponse;
import com.yqz.openblog.project.dto.ProjectUpsertRequest;
import com.yqz.openblog.project.entity.ProjectStatus;
import com.yqz.openblog.project.service.ProjectService;
import com.yqz.openblog.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
@CrossOrigin(origins = "*")
public class ProjectController {

    private final ProjectService projectService;
    private final CurrentUser currentUser;

    public ProjectController(ProjectService projectService, CurrentUser currentUser) {
        this.projectService = projectService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<PageResult<ProjectListItemResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(projectService.listPublished(page, size));
    }

    /**
     * 控制台管理列表：返回全部状态（含草稿），仅 ADMIN 可访问。
     * 注意字面路径 /admin 优先于 /{id} 匹配。
     */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<ProjectListItemResponse>> adminList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(projectService.listAll(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectDetailResponse> detail(@PathVariable Long id) {
        ProjectDetailResponse d = projectService.detail(id);
        // 草稿对非管理员隐藏（返回 404 而非泄露正文）；ADMIN 在控制台可见可编辑
        if (!currentUser.isAdmin() && d.getStatus() != ProjectStatus.PUBLISHED) {
            throw new BizException(4041, "项目不存在");
        }
        return ApiResponse.ok(d);
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
