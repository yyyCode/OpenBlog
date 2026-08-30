package com.yqz.openblog.smallcompany.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.smallcompany.dto.SmallCompanyDetailResponse;
import com.yqz.openblog.smallcompany.dto.SmallCompanyListItemResponse;
import com.yqz.openblog.smallcompany.dto.SmallCompanyUpsertRequest;
import com.yqz.openblog.smallcompany.entity.SmallCompanyStatus;
import com.yqz.openblog.smallcompany.service.SmallCompanyService;
import com.yqz.openblog.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/small-companies")
@CrossOrigin(origins = "*")
public class SmallCompanyController {

    private final SmallCompanyService smallCompanyService;
    private final CurrentUser currentUser;

    public SmallCompanyController(SmallCompanyService smallCompanyService, CurrentUser currentUser) {
        this.smallCompanyService = smallCompanyService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<PageResult<SmallCompanyListItemResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(smallCompanyService.listPublished(page, size));
    }

    /**
     * 控制台管理列表：返回全部状态（含草稿），仅 ADMIN 可访问。
     * 注意字面路径 /admin 优先于 /{id} 匹配。
     */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<SmallCompanyListItemResponse>> adminList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(smallCompanyService.listAll(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<SmallCompanyDetailResponse> detail(@PathVariable Long id) {
        SmallCompanyDetailResponse d = smallCompanyService.detail(id);
        // 草稿对非管理员隐藏（返回 404 而非泄露内容）；ADMIN 在控制台可见可编辑
        if (!currentUser.isAdmin() && d.getStatus() != SmallCompanyStatus.PUBLISHED) {
            throw new BizException(4041, "公司不存在");
        }
        return ApiResponse.ok(d);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SmallCompanyListItemResponse> create(@RequestBody @Valid SmallCompanyUpsertRequest req) {
        return ApiResponse.ok(smallCompanyService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SmallCompanyListItemResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid SmallCompanyUpsertRequest req) {
        return ApiResponse.ok(smallCompanyService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        smallCompanyService.delete(id);
        return ApiResponse.ok();
    }
}
