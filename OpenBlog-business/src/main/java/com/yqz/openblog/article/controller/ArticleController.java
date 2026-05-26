package com.yqz.openblog.article.controller;

import com.yqz.openblog.article.dto.ArticleCreateRequest;
import com.yqz.openblog.article.dto.ArticleDetailResponse;
import com.yqz.openblog.article.dto.ArticleListItemResponse;
import com.yqz.openblog.article.dto.ArticlePublishRequest;
import com.yqz.openblog.article.dto.ArticleUpdateRequest;
import com.yqz.openblog.article.service.ArticleService;
import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.common.web.ClientIpResolver;
import com.yqz.openblog.security.CurrentUser;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class ArticleController {

    private final ArticleService articleService;
    private final CurrentUser currentUser;

    public ArticleController(ArticleService articleService, CurrentUser currentUser) {
        this.articleService = articleService;
        this.currentUser = currentUser;
    }

    @GetMapping("/articles")
    public ApiResponse<PageResult<ArticleListItemResponse>> listPublished(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long categoryId) {
        return ApiResponse.ok(articleService.listPublished(page, size, categoryId));
    }

    @GetMapping("/articles/{articleId}")
    public ApiResponse<ArticleDetailResponse> detailPublished(@PathVariable("articleId") Long articleId,
                                                             HttpServletRequest request) {
        String clientIp = ClientIpResolver.resolve(request);
        return ApiResponse.ok(articleService.detailPublished(articleId, clientIp));
    }

    @PostMapping("/articles")
    @PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
    public ApiResponse<ArticleListItemResponse> createDraft(@RequestBody @Valid ArticleCreateRequest req) {
        Long uid = currentUser.userId();
        return ApiResponse.ok(articleService.createDraft(uid, req));
    }

    @PutMapping("/articles/{articleId}")
    @PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
    public ApiResponse<ArticleListItemResponse> updateArticle(@PathVariable("articleId") Long articleId,
                                                               @RequestBody @Valid ArticleUpdateRequest req) {
        Long uid = currentUser.userId();
        return ApiResponse.ok(articleService.updateArticle(uid, articleId, req));
    }

    @PostMapping("/articles/{articleId}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
    public ApiResponse<ArticleListItemResponse> submitForReview(@PathVariable("articleId") Long articleId,
                                                                @RequestBody(required = false) ArticlePublishRequest req) {
        Long uid = currentUser.userId();
        return ApiResponse.ok(articleService.publish(uid, articleId, req == null ? null : req.getPublishedAt()));
    }

    @PostMapping("/articles/{articleId}/unpublish")
    @PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
    public ApiResponse<Void> unpublish(@PathVariable("articleId") Long articleId) {
        Long uid = currentUser.userId();
        articleService.unpublishOrDelete(uid, articleId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/articles/{articleId}")
    @PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
    public ApiResponse<Void> deleteArticle(@PathVariable("articleId") Long articleId) {
        Long uid = currentUser.userId();
        articleService.unpublishOrDelete(uid, articleId);
        return ApiResponse.ok();
    }

    @GetMapping("/users/me/articles")
    @PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
    public ApiResponse<PageResult<ArticleListItemResponse>> listMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long uid = currentUser.userId();
        return ApiResponse.ok(articleService.listMine(uid, page, size));
    }

    @GetMapping("/users/me/articles/{articleId}")
    @PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
    public ApiResponse<ArticleDetailResponse> detailMine(@PathVariable("articleId") Long articleId,
                                                          HttpServletRequest request) {
        Long uid = currentUser.userId();
        String clientIp = ClientIpResolver.resolve(request);
        return ApiResponse.ok(articleService.detailMine(uid, articleId, clientIp));
    }
}
