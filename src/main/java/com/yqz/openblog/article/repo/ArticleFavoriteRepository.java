package com.yqz.openblog.article.repo;

import com.yqz.openblog.article.entity.ArticleFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArticleFavoriteRepository extends JpaRepository<ArticleFavorite, Long> {
    Optional<ArticleFavorite> findByArticleIdAndUserId(Long articleId, Long userId);
    void deleteByArticleIdAndUserId(Long articleId, Long userId);
}

