package com.yqz.openblog.search.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yqz.openblog.article.entity.Article;
import com.yqz.openblog.article.entity.ArticleStatus;
import com.yqz.openblog.article.repo.ArticleMapper;
import com.yqz.openblog.audit.annotation.AuditLog;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.search.core.SearchOps;
import com.yqz.openblog.search.dto.ArticleSearchReindexResult;
import com.yqz.openblog.search.index.SearchIndex;
import com.yqz.openblog.search.index.SearchIndexManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 全量重建文章索引：删除旧索引后，把库内所有已发布文章分批写入 ES。
 *
 * <p>用于「开启 ES 前已有存量文章」「索引 mapping 变更」「索引与库长期不一致」等场景。
 * 重建期间该索引搜索结果为空，属于管理端操作，不应在请求热路径上调用。</p>
 */
@Service
public class ArticleSearchReindexService {

    private static final Logger log = LoggerFactory.getLogger(ArticleSearchReindexService.class);

    /** 单批条数：与 indexBatch 的 bulk 粒度对齐，过大易触发 ES 请求体限制。 */
    private static final int BATCH_SIZE = 200;

    private final ArticleMapper articleMapper;
    private final ArticleIndexer articleIndexer;
    /**
     * SearchOps 与 SearchIndexManager 都只在 openblog.search.enabled=true 时注册，
     * 这里必须惰性获取，否则 ES 关闭时应用无法启动。
     */
    private final ObjectProvider<SearchIndexManager> indexManagerProvider;
    private final SearchIndex articleSearchIndex;
    private final ObjectProvider<SearchOps> searchOpsProvider;

    public ArticleSearchReindexService(ArticleMapper articleMapper,
                                       ArticleIndexer articleIndexer,
                                       ObjectProvider<SearchIndexManager> indexManagerProvider,
                                       @Qualifier("articleSearchIndex") SearchIndex articleSearchIndex,
                                       ObjectProvider<SearchOps> searchOpsProvider) {
        this.articleMapper = articleMapper;
        this.articleIndexer = articleIndexer;
        this.indexManagerProvider = indexManagerProvider;
        this.articleSearchIndex = articleSearchIndex;
        this.searchOpsProvider = searchOpsProvider;
    }

    /**
     * 删除并按主键顺序全量重建文章索引。
     *
     * @throws BizException ES 未启用或索引重建失败
     */
    @AuditLog(action = "SEARCH_REINDEX", entityType = "ArticleSearch", recordResult = true)
    public ArticleSearchReindexResult reindex() {
        SearchIndexManager indexManager = indexManagerProvider.getIfAvailable();
        if (searchOpsProvider.getIfAvailable() == null || indexManager == null) {
            throw new BizException(4000, "ES 搜索未启用（openblog.search.enabled=false）");
        }

        Long count = articleMapper.selectCount(Wrappers.<Article>lambdaQuery()
                .eq(Article::getStatus, ArticleStatus.PUBLISHED));
        long total = count == null ? 0L : count;

        if (!indexManager.recreateIndex(articleSearchIndex)) {
            throw new BizException(5000, "ES 索引重建失败，请检查 ES 连接");
        }
        if (total == 0) {
            log.info("全量重建文章索引完成：没有已发布文章，index={}", articleSearchIndex.indexName());
            return new ArticleSearchReindexResult(0, 0, 0, 0);
        }

        long lastId = 0L;
        long indexed = 0L;
        int batches = 0;
        int failedBatches = 0;
        while (true) {
            // 主键游标分页，避免深分页 offset 越来越慢
            List<Article> batch = articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                    .eq(Article::getStatus, ArticleStatus.PUBLISHED)
                    .gt(Article::getId, lastId)
                    .orderByAsc(Article::getId)
                    .last("limit " + BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            int written = articleIndexer.syncBatch(batch);
            indexed += written;
            batches++;
            if (written == 0) {
                failedBatches++;
            }
            lastId = batch.get(batch.size() - 1).getId();
        }

        if (failedBatches > 0) {
            log.warn("全量重建文章索引部分失败：total={}, indexed={}, batches={}, failedBatches={}",
                    total, indexed, batches, failedBatches);
        } else {
            log.info("全量重建文章索引完成：total={}, indexed={}, batches={}",
                    total, indexed, batches);
        }
        return new ArticleSearchReindexResult(total, indexed, batches, failedBatches);
    }
}
