package com.yqz.openblog.changelog.controller;

import com.yqz.openblog.changelog.dto.ChangelogDetailResponse;
import com.yqz.openblog.changelog.dto.ChangelogListItemResponse;
import com.yqz.openblog.changelog.dto.ChangelogUpsertRequest;
import com.yqz.openblog.changelog.service.ChangelogService;
import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/changelog")
@CrossOrigin(origins = "*")
public class ChangelogController {

    private final ChangelogService changelogService;
    private final CurrentUser currentUser;

    public ChangelogController(ChangelogService changelogService, CurrentUser currentUser) {
        this.changelogService = changelogService;
        this.currentUser = currentUser;
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
    public ApiResponse<ChangelogListItemResponse> create(@RequestBody @Valid ChangelogUpsertRequest req) {
        currentUser.userId();
        return ApiResponse.ok(changelogService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<ChangelogListItemResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid ChangelogUpsertRequest req) {
        currentUser.userId();
        return ApiResponse.ok(changelogService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        currentUser.userId();
        changelogService.delete(id);
        return ApiResponse.ok();
    }
}
