package com.yqz.openblog.category.controller;

import com.yqz.openblog.category.dto.CategoryFlatItemResponse;
import com.yqz.openblog.category.dto.CategoryTreeNodeResponse;
import com.yqz.openblog.category.dto.CategoryUpsertRequest;
import com.yqz.openblog.category.service.CategoryService;
import com.yqz.openblog.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@CrossOrigin(origins = "*")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/tree")
    public ApiResponse<List<CategoryTreeNodeResponse>> tree() {
        return ApiResponse.ok(categoryService.listTree());
    }

    @GetMapping
    public ApiResponse<List<CategoryFlatItemResponse>> listFlat() {
        return ApiResponse.ok(categoryService.listFlat());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CategoryFlatItemResponse> create(@RequestBody @Valid CategoryUpsertRequest req) {
        return ApiResponse.ok(categoryService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CategoryFlatItemResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid CategoryUpsertRequest req) {
        return ApiResponse.ok(categoryService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ApiResponse.ok();
    }
}
