package com.yqz.openblog.article.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@TableName("article_bodies")
@Table(name = "article_bodies", indexes = {
        @Index(name = "idx_article_bodies_word_count", columnList = "word_count")
})
public class ArticleBody {

    @TableId(value = "article_id", type = IdType.INPUT)
    @Id
    private Long articleId;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String contentMarkdown;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String contentHtml;

    @Column
    private Integer wordCount;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (wordCount == null) wordCount = 0;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public ArticleBody() {
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public String getContentMarkdown() {
        return contentMarkdown;
    }

    public void setContentMarkdown(String contentMarkdown) {
        this.contentMarkdown = contentMarkdown;
    }

    public String getContentHtml() {
        return contentHtml;
    }

    public void setContentHtml(String contentHtml) {
        this.contentHtml = contentHtml;
    }

    public Integer getWordCount() {
        return wordCount;
    }

    public void setWordCount(Integer wordCount) {
        this.wordCount = wordCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
