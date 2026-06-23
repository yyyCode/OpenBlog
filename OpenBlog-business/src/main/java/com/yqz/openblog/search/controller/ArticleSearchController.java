package com.yqz.openblog.search.controller;

import com.yqz.openblog.article.dto.ArticleListItemResponse;
import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.search.service.ArticleSearchService;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
@RequestMapping("/api/v1")
public class ArticleSearchController {

    private final ArticleSearchService searchService;

    public ArticleSearchController(ArticleSearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * 全文搜索已发布文章。
     *
     * @param keyword 搜索关键词
     * @param page    页码（从 0 开始）
     * @param size    每页条数（默认 20）
     */
    @GetMapping("/articles/search")
    public ApiResponse<PageResult<ArticleListItemResponse>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String kw = keyword.trim();
        if (kw.isEmpty()) {
            return ApiResponse.ok(new PageResult<>(Collections.emptyList(), 0, size, 0));
        }
        return ApiResponse.ok(searchService.search(kw, page, size));
    }
}
