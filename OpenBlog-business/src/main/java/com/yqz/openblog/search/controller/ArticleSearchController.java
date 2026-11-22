package com.yqz.openblog.search.controller;

import com.yqz.openblog.article.dto.ArticleListItemResponse;
import com.yqz.openblog.article.entity.ArticleType;
import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.search.service.ArticleSearchService;
import org.springframework.web.bind.annotation.*;

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
     * @param keyword    搜索关键词
     * @param page       页码（从 0 开始）
     * @param size       每页条数（默认 20，最大 50）
     * @param categoryId 分类过滤（可选，精确匹配）
     * @param type       文章类型过滤（可选：ARTICLE / JOB_NAV / BUG_CASE）
     */
    @GetMapping("/articles/search")
    public ApiResponse<PageResult<ArticleListItemResponse>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String type) {
        return ApiResponse.ok(searchService.search(keyword, page, size, categoryId, parseType(type)));
    }

    private static ArticleType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return ArticleType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(4000, "无效的文章类型：" + type);
        }
    }
}
