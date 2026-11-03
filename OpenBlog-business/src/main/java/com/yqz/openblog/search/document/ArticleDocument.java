package com.yqz.openblog.search.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;

/**
 * 文章索引文档。
 *
 * <p>实际读写的索引名由 {@code ArticleSearchIndexConfig} 中的 {@code SearchIndex} 决定
 * （{@code index-prefix + articles}）；这里的 {@code indexName} 仅用于生成 mapping。</p>
 */
@Document(indexName = "openblog_articles")
public class ArticleDocument {

    @Id
    private Long id;

    /** 标题，IK 分词 */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String title;

    /** 摘要，IK 分词 */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String summary;

    /** Markdown 正文，IK 分词 */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String contentMarkdown;

    /** 分类名（精确匹配，用于展示与过滤） */
    @Field(type = FieldType.Keyword)
    private String categoryName;

    /** 分类 ID（精确匹配，用于过滤） */
    @Field(type = FieldType.Long)
    private Long categoryId;

    /** 作者 ID */
    @Field(type = FieldType.Long)
    private Long authorId;

    /** 作者名 */
    @Field(type = FieldType.Keyword)
    private String authorName;

    /** 文章状态；只有 PUBLISHED 会被写入索引，保留该字段便于过滤与排查 */
    @Field(type = FieldType.Keyword)
    private String status;

    /** 发布时间 */
    @Field(type = FieldType.Date, format = DateFormat.date_optional_time)
    private Instant publishedAt;

    /** 浏览量 */
    @Field(type = FieldType.Long)
    private Long viewCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getContentMarkdown() {
        return contentMarkdown;
    }

    public void setContentMarkdown(String contentMarkdown) {
        this.contentMarkdown = contentMarkdown;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }
}
