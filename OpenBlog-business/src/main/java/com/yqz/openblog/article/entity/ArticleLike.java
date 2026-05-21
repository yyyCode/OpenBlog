package com.yqz.openblog.article.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@TableName("article_likes")
@Table(name = "article_likes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_article_likes_article_user", columnNames = {"article_id", "user_id"})
})
public class ArticleLike {

    @TableId(value = "id", type = IdType.AUTO)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = Instant.now();
    }

    protected ArticleLike() {
    }

    public ArticleLike(Long articleId, Long userId) {
        this.articleId = articleId;
        this.userId = userId;
    }

    public Long getId() {
        return id;
    }

    public Long getArticleId() {
        return articleId;
    }

    public Long getUserId() {
        return userId;
    }
}

