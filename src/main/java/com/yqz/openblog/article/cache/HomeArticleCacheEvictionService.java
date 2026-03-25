package com.yqz.openblog.article.cache;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

/**
 * 首页相关 Redis 缓存失效（需通过 Spring 代理调用 {@link CacheEvict} 才生效）。
 */
@Service
public class HomeArticleCacheEvictionService {

    @CacheEvict(cacheNames = "homeArticleList", allEntries = true)
    public void evictFirstPageList() {
        // no-op
    }

    @CacheEvict(cacheNames = "homeArticleDetail", key = "#articleId")
    public void evictDetail(Long articleId) {
        // no-op
    }
}
