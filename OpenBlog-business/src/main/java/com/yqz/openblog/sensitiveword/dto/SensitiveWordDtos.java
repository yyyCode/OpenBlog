package com.yqz.openblog.sensitiveword.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** 敏感词管理相关 DTO（集中定义，避免大量单字段文件）。 */
public final class SensitiveWordDtos {

    private SensitiveWordDtos() {
    }

    public record UpsertRequest(
            @NotBlank @Size(max = 128) String word,
            @Size(max = 64) String category,
            Integer status,
            @Size(max = 255) String remark) {
    }

    public record PublishRequest(String remark, Boolean allowEmpty) {
    }

    public record RollbackRequest(String remark) {
    }

    public record CheckRequest(@NotBlank @Size(max = 20_000) String text) {
    }

    public record BatchDeleteRequest(List<Long> ids) {
    }

    public record IdResponse(Long id) {
    }

    public record ItemResponse(
            Long id,
            String word,
            String category,
            Integer status,
            String remark,
            String createdBy,
            String updatedBy,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record PendingItem(String action, String word) {
    }

    public record PendingResponse(int added, int removed, int updated, List<PendingItem> items) {
    }

    public record VersionResponse(
            Long version,
            String kind,
            String status,
            Long sourceVersion,
            Integer wordCount,
            String checksum,
            String changeSummary,
            String remark,
            String createdBy,
            Instant publishedAt) {
    }

    public record VersionDiffResponse(
            long version,
            int added,
            int removed,
            List<String> addedWords,
            List<String> removedWords) {
    }

    public record ImportRow(int line, String word, String status, String reason) {
    }

    public record ImportPreviewResponse(
            int total,
            int newCount,
            int exist,
            int invalid,
            int ignored,
            List<ImportRow> rows) {
    }

    public record ImportResult(int inserted, int skipped, int invalid, boolean needPublish) {
    }

    public record NodeStatus(int total, int upToDate, List<String> stale) {
    }

    public record ShadowStatus(
            String engine,
            long samples,
            long mismatches,
            double primaryAvgUs,
            double shadowAvgUs) {
    }

    public record StatusResponse(
            String state,
            String engine,
            Long publishedVersion,
            long localVersion,
            int wordCount,
            double avgMatchUs,
            Instant lastPublishedAt,
            PendingResponse pending,
            NodeStatus nodes,
            ShadowStatus shadow,
            String configuredEngine,
            String engineOverride) {
    }

    public record CheckResponse(
            boolean hit,
            String filteredText,
            List<String> hitWords,
            long version,
            String engine,
            long costMs,
            long costUs) {
    }

    public record PublishResponse(
            Long version,
            int wordCount,
            String changeSummary,
            int expectedEffectiveSeconds) {
    }

    public record RollbackResponse(Long newVersion, Long rolledBackTo, int wordCount) {
    }

    /** engine 为空 / default 表示清除覆盖、跟随配置默认引擎。 */
    public record EngineSwitchRequest(String engine) {
    }

    public record EngineSwitchResponse(
            String engine,
            String engineOverride,
            String previousEngine,
            int wordCount,
            long buildMs,
            boolean applied,
            int propagateSeconds) {
    }
}
