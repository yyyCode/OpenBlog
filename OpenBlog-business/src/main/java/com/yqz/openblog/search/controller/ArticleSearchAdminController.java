package com.yqz.openblog.search.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.search.dto.ArticleSearchReindexResult;
import com.yqz.openblog.search.service.ArticleSearchReindexService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 搜索索引管理端接口（仅 ADMIN）。
 */
@RestController
@RequestMapping("/api/v1/admin/search")
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class ArticleSearchAdminController {

    private final ArticleSearchReindexService reindexService;

    public ArticleSearchAdminController(ArticleSearchReindexService reindexService) {
        this.reindexService = reindexService;
    }

    /**
     * 全量重建文章索引：删除旧索引后，把库内所有已发布文章重新写入 ES。
     *
     * <p>重建期间搜索结果为空，属于低频管理操作。</p>
     */
    @PostMapping("/reindex")
    public ApiResponse<ArticleSearchReindexResult> reindex() {
        return ApiResponse.ok(reindexService.reindex());
    }
}
