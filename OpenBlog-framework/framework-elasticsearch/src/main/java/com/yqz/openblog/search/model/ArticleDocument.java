package com.yqz.openblog.search.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * 文章索引文档模型。
 * 索引名由配置前缀 + 业务名拼接，实际运行时由调用方传入。
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

    /** 分类名（精确匹配，用于过滤） */
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

    /** 发布时间 */
    @Field(type = FieldType.Date)
    private String publishedAt;

    /** 浏览量 */
    @Field(type = FieldType.Long)
    private Long viewCount;

    // --- getters and setters ---

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

    public String getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(String publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }
}
