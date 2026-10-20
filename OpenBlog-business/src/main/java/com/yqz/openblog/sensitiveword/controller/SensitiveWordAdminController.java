package com.yqz.openblog.sensitiveword.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.sensitiveword.dto.SensitiveWordDtos;
import com.yqz.openblog.sensitiveword.service.SensitiveWordAdminService;
import com.yqz.openblog.sensitiveword.service.SensitiveWordPublishService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/sensitive-words")
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class SensitiveWordAdminController {

    private final SensitiveWordAdminService adminService;
    private final SensitiveWordPublishService publishService;

    public SensitiveWordAdminController(SensitiveWordAdminService adminService,
                                        SensitiveWordPublishService publishService) {
        this.adminService = adminService;
        this.publishService = publishService;
    }

    @GetMapping
    public ApiResponse<PageResult<SensitiveWordDtos.ItemResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.list(keyword, category, status, page, size));
    }

    @GetMapping("/categories")
    public ApiResponse<List<String>> categories() {
        return ApiResponse.ok(adminService.categories());
    }

    @PostMapping
    public ApiResponse<SensitiveWordDtos.IdResponse> create(
            @RequestBody @Valid SensitiveWordDtos.UpsertRequest req) {
        return ApiResponse.ok(new SensitiveWordDtos.IdResponse(adminService.create(req)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @RequestBody @Valid SensitiveWordDtos.UpsertRequest req) {
        adminService.update(id, req);
        return ApiResponse.ok();
    }

    @DeleteMapping("/batch")
    public ApiResponse<Integer> batchDelete(@RequestBody SensitiveWordDtos.BatchDeleteRequest req) {
        return ApiResponse.ok(adminService.batchDelete(req.ids()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SensitiveWordDtos.ImportPreviewResponse> previewImport(
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(adminService.previewImport(file));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SensitiveWordDtos.ImportResult> importFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "APPEND") String mode,
            @RequestParam(defaultValue = "false") boolean confirmReplace) {
        return ApiResponse.ok(adminService.importFile(file, mode, confirmReplace));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        byte[] csv = adminService.exportCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sensitive-words.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }

    @GetMapping("/pending")
    public ApiResponse<SensitiveWordDtos.PendingResponse> pending() {
        return ApiResponse.ok(adminService.pending());
    }

    @PostMapping("/publish")
    public ApiResponse<SensitiveWordDtos.PublishResponse> publish(
            @RequestBody(required = false) SensitiveWordDtos.PublishRequest req) {
        String remark = req == null ? null : req.remark();
        boolean allowEmpty = req != null && Boolean.TRUE.equals(req.allowEmpty());
        return ApiResponse.ok(publishService.publish(remark, allowEmpty));
    }

    @GetMapping("/versions")
    public ApiResponse<PageResult<SensitiveWordDtos.VersionResponse>> versions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.versions(page, size));
    }

    @GetMapping("/versions/{version}/diff")
    public ApiResponse<SensitiveWordDtos.VersionDiffResponse> versionDiff(@PathVariable long version) {
        return ApiResponse.ok(adminService.versionDiff(version));
    }

    @PostMapping("/versions/{version}/rollback")
    public ApiResponse<SensitiveWordDtos.RollbackResponse> rollback(
            @PathVariable long version,
            @RequestBody(required = false) SensitiveWordDtos.RollbackRequest req) {
        return ApiResponse.ok(publishService.rollback(version, req == null ? null : req.remark()));
    }

    @PostMapping("/versions/{version}/sync")
    public ApiResponse<Void> sync(@PathVariable long version) {
        publishService.sync(version);
        return ApiResponse.ok();
    }

    @GetMapping("/status")
    public ApiResponse<SensitiveWordDtos.StatusResponse> status() {
        return ApiResponse.ok(adminService.status());
    }

    /** engine=default / 空表示清除覆盖，跟随配置文件中的默认引擎。 */
    @PutMapping("/engine")
    public ApiResponse<SensitiveWordDtos.EngineSwitchResponse> switchEngine(
            @RequestBody(required = false) SensitiveWordDtos.EngineSwitchRequest req) {
        return ApiResponse.ok(adminService.switchEngine(req == null ? null : req.engine()));
    }

    @PostMapping("/check")
    public ApiResponse<SensitiveWordDtos.CheckResponse> check(
            @RequestBody @Valid SensitiveWordDtos.CheckRequest req) {
        return ApiResponse.ok(adminService.check(req.text()));
    }
}
