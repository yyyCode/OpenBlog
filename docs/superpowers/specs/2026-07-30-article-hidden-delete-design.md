# Article Hidden Status & Physical Delete Design

## Overview

Replace the current soft-delete (`DELETED` status) with:
1. A new `HIDDEN` status for unpublishing articles (reversible)
2. Physical deletion (`DELETE` endpoint removes DB rows permanently)

## Current State

- `ArticleStatus`: DRAFT, SCHEDULED, PUBLISHED, DELETED
- Both `unpublish` and `delete` endpoints call the same `unpublishOrDelete()` → sets DELETED
- No real "unpublish" button in frontend, only "delete"
- DELETED articles are filtered out everywhere

## Target State

### Status Flow

```
DRAFT → PUBLISHED ⇄ HIDDEN
DRAFT → SCHEDULED → PUBLISHED
```

- `HIDDEN`: article was published but is now taken down, not visible to public
- `DRAFT`: never-published article being edited
- Physical delete removes `articles` + `article_bodies` rows only (no cascade)

### Backend Changes

#### `ArticleStatus.java`
- Replace `DELETED` with `HIDDEN`

#### `ArticleController.java`
- `DELETE /articles/{id}` → physical delete (new method)
- `POST /articles/{id}/unpublish` → set status to HIDDEN (new method)
- `POST /articles/{id}/publish` → allow HIDDEN → PUBLISHED transition

#### `ArticleService.java`
- Split `unpublishOrDelete()` into:
  - `hide(authorId, articleId)` — sets status to HIDDEN, evicts cache, removes from ES
  - `deletePermanently(authorId, articleId)` — deletes from `articles` + `article_bodies`, evicts cache, removes from ES
- `publish()`: allow transitioning from HIDDEN (was previously blocked)
- `listMine()`: include HIDDEN in visible statuses
- `detailMine()`: remove DELETED check, allow HIDDEN articles
- `updateArticle()`: allow editing HIDDEN articles

#### Database
- Existing articles with status `DELETED` need migration to `HIDDEN`

### Frontend Changes

#### `admin.js`
- `deleteMyArticle(id)` → maps to DELETE (now physical)
- Add `unpublishArticle(id)` → POST unpublish
- Add `publishArticle(id)` → POST publish (already exists, confirm it works)

#### `ConsoleArticleManageView.vue`
- Delete button shows confirm/cancel dialog before executing
- Add "hide" button for PUBLISHED articles
- Add "publish" button for HIDDEN articles
- List shows HIDDEN status label

### What is NOT deleted on physical delete
- Comments (remain orphaned in DB)
- Likes (remain orphaned in DB)
- Favorites (remain orphaned in DB)
- Cover media file (remains in storage)
