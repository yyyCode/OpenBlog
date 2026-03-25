package com.yqz.openblog.article.controller;

import com.yqz.openblog.article.dto.ArticleCreateRequest;
import com.yqz.openblog.article.dto.ArticleDetailResponse;
import com.yqz.openblog.article.dto.ArticleListItemResponse;
import com.yqz.openblog.article.dto.ArticleUpdateRequest;
import com.yqz.openblog.article.service.ArticleService;
import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.security.CurrentUser;
import com.yqz.openblog.security.TokenHashUtil;

import jakarta.validation.Valid;

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
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(articleService.listPublished(page, size));
    }

    @GetMapping("/articles/{articleId}")
    public ApiResponse<ArticleDetailResponse> detailPublished(@PathVariable("articleId") Long articleId,
                                                             HttpServletRequest request) {
        String viewerKey = buildViewerKey(request);
        return ApiResponse.ok(articleService.detailPublished(articleId, viewerKey));
    }

    @PostMapping("/articles")
    public ApiResponse<ArticleListItemResponse> createDraft(@RequestBody @Valid ArticleCreateRequest req) {
        Long uid = currentUser.userId();
        return ApiResponse.ok(articleService.createDraft(uid, req));
    }

    @PutMapping("/articles/{articleId}")
    public ApiResponse<ArticleListItemResponse> updateArticle(@PathVariable("articleId") Long articleId,
                                                               @RequestBody @Valid ArticleUpdateRequest req) {
        Long uid = currentUser.userId();
        return ApiResponse.ok(articleService.updateArticle(uid, articleId, req));
    }

    @PostMapping("/articles/{articleId}/publish")
    public ApiResponse<ArticleListItemResponse> submitForReview(@PathVariable("articleId") Long articleId) {
        Long uid = currentUser.userId();
        return ApiResponse.ok(articleService.publish(uid, articleId));
    }

    @PostMapping("/articles/{articleId}/unpublish")
    public ApiResponse<Void> unpublish(@PathVariable("articleId") Long articleId) {
        Long uid = currentUser.userId();
        articleService.unpublishOrDelete(uid, articleId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/articles/{articleId}")
    public ApiResponse<Void> deleteArticle(@PathVariable("articleId") Long articleId) {
        Long uid = currentUser.userId();
        articleService.unpublishOrDelete(uid, articleId);
        return ApiResponse.ok();
    }

    @GetMapping("/users/me/articles")
    public ApiResponse<PageResult<ArticleListItemResponse>> listMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long uid = currentUser.userId();
        return ApiResponse.ok(articleService.listMine(uid, page, size));
    }

    @GetMapping("/users/me/articles/{articleId}")
    public ApiResponse<ArticleDetailResponse> detailMine(@PathVariable("articleId") Long articleId,
                                                          HttpServletRequest request) {
        Long uid = currentUser.userId();
        String viewerKey = buildViewerKey(request);
        return ApiResponse.ok(articleService.detailMine(uid, articleId, viewerKey));
    }

    private String buildViewerKey(HttpServletRequest request) {
        Long uid = currentUser.userId();
        if (uid != null) {
            return "u:" + uid;
        }

        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        if (ip == null) ip = "";

        String ua = request.getHeader("User-Agent");
        if (ua == null) ua = "";

        String raw = ip + "|" + ua;
        return "anon:" + TokenHashUtil.sha256Hex(raw);
    }
}
