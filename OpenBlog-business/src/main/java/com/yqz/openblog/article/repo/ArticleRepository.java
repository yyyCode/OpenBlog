package com.yqz.openblog.article.repo;

import com.yqz.openblog.article.entity.Article;
import com.yqz.openblog.article.entity.ArticleStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    Page<Article> findByStatus(ArticleStatus status, Pageable pageable);

    List<Article> findByAuthorIdAndStatusIn(Long authorId, List<ArticleStatus> statuses);

    Page<Article> findByAuthorIdAndStatusIn(Long authorId, List<ArticleStatus> statuses, Pageable pageable);

    Optional<Article> findByIdAndStatus(Long id, ArticleStatus status);

    Optional<Article> findByIdAndAuthorId(Long id, Long authorId);

    @Modifying
    @Query("update Article a set a.viewCount = coalesce(a.viewCount, 0) + 1 where a.id = :articleId")
    int incrementViewCount(@Param("articleId") Long articleId);
}

