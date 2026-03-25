package com.yqz.openblog.article.cache;

import com.yqz.openblog.article.dto.ArticleDetailResponse;
import com.yqz.openblog.article.dto.ArticleListItemResponse;
import com.yqz.openblog.article.service.ArticleService;
import com.yqz.openblog.common.PageResult;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * 首页与公开文章详情读路径缓存（避免循环依赖，对 {@link ArticleService} 使用懒加载）。
 */
@Service
public class CachedHomeArticleQueryService {

    private final ArticleService articleService;

    public CachedHomeArticleQueryService(@Lazy ArticleService articleService) {
        this.articleService = articleService;
    }

    @Cacheable(cacheNames = "homeArticleList", unless = "#result == null")
    public PageResult<ArticleListItemResponse> listFirstPublishedPage() {
        return articleService.listPublished(0, 1);
    }

    @Cacheable(cacheNames = "homeArticleDetail", key = "#id")
    public ArticleDetailResponse loadPublishedDetailSnapshot(Long id) {
        return articleService.loadPublishedDetailSnapshot(id);
    }
}
