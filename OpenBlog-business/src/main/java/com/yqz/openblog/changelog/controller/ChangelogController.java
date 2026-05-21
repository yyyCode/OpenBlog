package com.yqz.openblog.changelog.controller;

import com.yqz.openblog.changelog.dto.ChangelogDetailResponse;
import com.yqz.openblog.changelog.dto.ChangelogListItemResponse;
import com.yqz.openblog.changelog.dto.ChangelogUpsertRequest;
import com.yqz.openblog.changelog.service.ChangelogService;
import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.PageResult;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/changelog")
@CrossOrigin(origins = "*")
public class ChangelogController {

    private final ChangelogService changelogService;

    public ChangelogController(ChangelogService changelogService) {
        this.changelogService = changelogService;
    }

    @GetMapping
    public ApiResponse<PageResult<ChangelogListItemResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(changelogService.listPublished(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ChangelogDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(changelogService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ChangelogListItemResponse> create(@RequestBody @Valid ChangelogUpsertRequest req) {
        return ApiResponse.ok(changelogService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ChangelogListItemResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid ChangelogUpsertRequest req) {
        return ApiResponse.ok(changelogService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        changelogService.delete(id);
        return ApiResponse.ok();
    }
}
