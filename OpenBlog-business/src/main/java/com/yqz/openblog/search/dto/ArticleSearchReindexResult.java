package com.yqz.openblog.search.dto;

/**
 * 全量重建文章索引的结果统计。
 *
 * @param total         待索引的已发布文章总数
 * @param indexed       实际写入 ES 的文档数
 * @param batches       处理的批次数
 * @param failedBatches 写入失败的批次数（ES 不可用时通常等于 batches）
 */
public record ArticleSearchReindexResult(long total, long indexed, int batches, int failedBatches) {
}
