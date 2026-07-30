# Article Type Field — Job Navigation Design

## Overview

Add a `type` field to `articles` table to support article-type pages like 求职导航, separate from blog articles.

## Design

### Database

Add `type` column to `articles`:

```sql
ALTER TABLE articles ADD COLUMN type VARCHAR(16) NOT NULL DEFAULT 'ARTICLE' AFTER status;
```

### ArticleType Enum

```java
public enum ArticleType {
    ARTICLE,   // 博客文章
    JOB_NAV    // 求职导航
}
```

### Backend

- `Article` entity: add `type` field
- `ArticleService.listPublished()`: filter `type = ARTICLE`
- `ArticleService.listByType(ArticleType type, page, size)`: new method for public type-filtered listing
- `ArticleCreateRequest` / `ArticleUpdateRequest`: add optional `type` field
- `ArticleController`: new `GET /articles/type/{type}` endpoint

### Frontend

- `BlogHeader.vue`: 求职导航 `href="#"` → `router-link to="/jobs"`
- Router: add `/jobs` route
- New `JobNavView.vue`: list job nav articles, reuses `ArticleCard` component
- Article detail: reuse existing `ArticleDetailView.vue`
- Console: article compose view adds type selector
