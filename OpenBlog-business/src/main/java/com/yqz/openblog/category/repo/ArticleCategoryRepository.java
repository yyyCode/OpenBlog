package com.yqz.openblog.category.repo;

import com.yqz.openblog.category.entity.ArticleCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArticleCategoryRepository extends JpaRepository<ArticleCategory, Long> {

    List<ArticleCategory> findAllByOrderBySortOrderAscIdAsc();

    boolean existsByParentId(Long parentId);
}
