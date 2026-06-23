package com.yqz.openblog.category.service;

import com.yqz.openblog.article.repo.ArticleMapper;
import com.yqz.openblog.category.dto.CategoryFlatItemResponse;
import com.yqz.openblog.category.dto.CategoryTreeNodeResponse;
import com.yqz.openblog.category.dto.CategoryUpsertRequest;
import com.yqz.openblog.category.entity.ArticleCategory;
import com.yqz.openblog.category.repo.ArticleCategoryRepository;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.common.TreeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yqz.openblog.article.entity.Article;

import java.util.*;

@Service
public class CategoryService {

    private final ArticleCategoryRepository categoryRepository;
    private final ArticleMapper articleMapper;

    public CategoryService(ArticleCategoryRepository categoryRepository, ArticleMapper articleMapper) {
        this.categoryRepository = categoryRepository;
        this.articleMapper = articleMapper;
    }

    public List<CategoryTreeNodeResponse> listTree() {
        List<ArticleCategory> all = categoryRepository.findAllByOrderBySortOrderAscIdAsc();
        return buildTree(all);
    }

    public List<CategoryFlatItemResponse> listFlat() {
        List<ArticleCategory> all = categoryRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, ArticleCategory> byId = indexById(all);
        List<CategoryFlatItemResponse> items = new ArrayList<>();
        for (ArticleCategory c : all) {
            CategoryFlatItemResponse r = new CategoryFlatItemResponse();
            r.setId(c.getId());
            r.setName(c.getName());
            r.setParentId(c.getParentId());
            r.setSortOrder(c.getSortOrder());
            r.setPath(buildPathNames(c.getId(), byId));
            items.add(r);
        }
        return items;
    }

    @Transactional
    public CategoryFlatItemResponse create(CategoryUpsertRequest req) {
        ArticleCategory c = new ArticleCategory();
        apply(c, req, null);
        c = categoryRepository.save(c);
        return toFlatItem(c, indexById(categoryRepository.findAllByOrderBySortOrderAscIdAsc()));
    }

    @Transactional
    public CategoryFlatItemResponse update(Long id, CategoryUpsertRequest req) {
        ArticleCategory c = categoryRepository.findById(id)
                .orElseThrow(() -> new BizException(4041, "分类不存在"));
        apply(c, req, id);
        c = categoryRepository.save(c);
        return toFlatItem(c, indexById(categoryRepository.findAllByOrderBySortOrderAscIdAsc()));
    }

    @Transactional
    public void delete(Long id) {
        ArticleCategory c = categoryRepository.findById(id)
                .orElseThrow(() -> new BizException(4041, "分类不存在"));
        if (categoryRepository.existsByParentId(c.getId())) {
            throw new BizException(4091, "请先删除或移动子分类");
        }
        LambdaQueryWrapper<Article> w = Wrappers.lambdaQuery();
        w.eq(Article::getCategoryId, c.getId());
        Long count = articleMapper.selectCount(w);
        if (count != null && count > 0) {
            throw new BizException(4091, "该分类下仍有文章，无法删除");
        }
        categoryRepository.deleteById(id);
    }

    public void validateCategoryId(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        if (!categoryRepository.existsById(categoryId)) {
            throw new BizException(4000, "分类不存在");
        }
    }

    public CategoryMeta resolveMeta(Long categoryId) {
        if (categoryId == null) {
            return CategoryMeta.empty();
        }
        Map<Long, ArticleCategory> byId = indexById(categoryRepository.findAllByOrderBySortOrderAscIdAsc());
        ArticleCategory c = byId.get(categoryId);
        if (c == null) {
            return CategoryMeta.empty();
        }
        List<String> path = buildPathNames(categoryId, byId);
        return new CategoryMeta(categoryId, c.getName(), path);
    }

    public Set<Long> collectSelfAndDescendantIds(Long categoryId) {
        if (categoryId == null) {
            return Collections.emptySet();
        }
        List<ArticleCategory> all = categoryRepository.findAllByOrderBySortOrderAscIdAsc();
        List<Long> ids = all.stream().map(ArticleCategory::getId).toList();
        Map<Long, List<Long>> childrenMap = TreeUtils.buildChildrenMap(ids, id -> {
            ArticleCategory cat = all.stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null);
            return cat != null ? cat.getParentId() : null;
        });
        Set<Long> out = new LinkedHashSet<>();
        TreeUtils.collectDescendants(categoryId, childrenMap, out);
        return out;
    }

    private void apply(ArticleCategory c, CategoryUpsertRequest req, Long excludeId) {
        String name = req.getName() == null ? "" : req.getName().trim();
        if (name.isEmpty()) {
            throw new BizException(4000, "分类名称不能为空");
        }
        Long parentId = req.getParentId();
        if (parentId != null) {
            if (excludeId != null && parentId.equals(excludeId)) {
                throw new BizException(4000, "父分类不能是自己");
            }
            if (!categoryRepository.existsById(parentId)) {
                throw new BizException(4000, "父分类不存在");
            }
            if (excludeId != null && isDescendant(excludeId, parentId)) {
                throw new BizException(4000, "父分类不能是当前分类的子节点");
            }
        }
        c.setName(name);
        c.setParentId(parentId);
        c.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
    }

    private boolean isDescendant(Long ancestorId, Long nodeId) {
        Map<Long, ArticleCategory> byId = indexById(categoryRepository.findAllByOrderBySortOrderAscIdAsc());
        return TreeUtils.isDescendant(ancestorId, nodeId, byId, ArticleCategory::getParentId);
    }

    private List<CategoryTreeNodeResponse> buildTree(List<ArticleCategory> all) {
        Map<Long, CategoryTreeNodeResponse> nodes = new HashMap<>();
        for (ArticleCategory c : all) {
            CategoryTreeNodeResponse n = new CategoryTreeNodeResponse();
            n.setId(c.getId());
            n.setName(c.getName());
            n.setParentId(c.getParentId());
            n.setSortOrder(c.getSortOrder());
            nodes.put(c.getId(), n);
        }
        List<CategoryTreeNodeResponse> roots = new ArrayList<>();
        for (ArticleCategory c : all) {
            CategoryTreeNodeResponse n = nodes.get(c.getId());
            if (c.getParentId() == null) {
                roots.add(n);
                continue;
            }
            CategoryTreeNodeResponse parent = nodes.get(c.getParentId());
            if (parent == null) {
                roots.add(n);
            } else {
                parent.getChildren().add(n);
            }
        }
        return roots;
    }

    private Map<Long, ArticleCategory> indexById(List<ArticleCategory> all) {
        return TreeUtils.indexById(all, ArticleCategory::getId);
    }

    private List<String> buildPathNames(Long categoryId, Map<Long, ArticleCategory> byId) {
        return TreeUtils.buildPathNames(categoryId, byId, ArticleCategory::getParentId, ArticleCategory::getName);
    }

    private CategoryFlatItemResponse toFlatItem(ArticleCategory c, Map<Long, ArticleCategory> byId) {
        CategoryFlatItemResponse r = new CategoryFlatItemResponse();
        r.setId(c.getId());
        r.setName(c.getName());
        r.setParentId(c.getParentId());
        r.setSortOrder(c.getSortOrder());
        r.setPath(buildPathNames(c.getId(), byId));
        return r;
    }

    public static final class CategoryMeta {
        private final Long categoryId;
        private final String categoryName;
        private final List<String> categoryPath;

        private CategoryMeta(Long categoryId, String categoryName, List<String> categoryPath) {
            this.categoryId = categoryId;
            this.categoryName = categoryName;
            this.categoryPath = categoryPath;
        }

        public static CategoryMeta empty() {
            return new CategoryMeta(null, null, List.of());
        }

        public Long getCategoryId() {
            return categoryId;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public List<String> getCategoryPath() {
            return categoryPath;
        }
    }
}
