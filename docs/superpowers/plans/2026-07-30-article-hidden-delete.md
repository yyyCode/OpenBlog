# Article Hidden Status & Physical Delete — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace soft-delete (`DELETED` status) with `HIDDEN` status for unpublishing and true physical deletion for the DELETE endpoint.

**Architecture:** Change `ArticleStatus.DELETED` → `HIDDEN` in the enum; split `unpublishOrDelete()` into `hide()` (sets HIDDEN) and `deletePermanently()` (removes from `articles` + `article_bodies`); update all status guards across `ArticleService` and `ArticleController`; add confirm dialog and hide/publish toggle buttons in the frontend.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, Vue 3

---

### Task 1: Update ArticleStatus enum

**Files:**
- Modify: `OpenBlog-business/src/main/java/com/yzq/openblog/article/entity/ArticleStatus.java`

- [ ] **Step 1: Replace DELETED with HIDDEN**

```java
package com.yqz.openblog.article.entity;

public enum ArticleStatus {
    DRAFT,
    /**
     * 已预约（到达 scheduledAt 前不可见），由定时任务自动转为 PUBLISHED。
     */
    SCHEDULED,
    PUBLISHED,
    /**
     * 已隐藏（下架），不对公众展示，作者可在控制台重新发布。
     */
    HIDDEN
}
```

- [ ] **Step 2: Compile to verify no compile errors from the enum change alone**

Run: `cd OpenBlog-business && mvn compile -q 2>&1 | head -30`
Expected: Compile errors in ArticleService.java (references to `ArticleStatus.DELETED`) — expected at this stage

- [ ] **Step 3: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/article/entity/ArticleStatus.java
git commit -m "refactor(article): rename DELETED status to HIDDEN"
```

---

### Task 2: ArticleService — split unpublishOrDelete into hide() and deletePermanently()

**Files:**
- Modify: `OpenBlog-business/src/main/java/com/yqz/openblog/article/service/ArticleService.java:419-437`

- [ ] **Step 1: Replace unpublishOrDelete with hide() method**

Replace the old `unpublishOrDelete` method (lines 419-437):

```java
@Transactional
public void hide(Long authorId, Long articleId) {
    Article a = articleMapper.selectById(articleId);
    if (a == null) {
        throw new BizException(4041, "文章不存在");
    }
    if (!a.getAuthorId().equals(authorId)) {
        throw new BizException(4031, "无权限");
    }
    if (a.getStatus() == ArticleStatus.HIDDEN) {
        return;
    }
    a.setStatus(ArticleStatus.HIDDEN);
    a.setScheduledAt(null);
    articleMapper.updateById(a);
    publishedContentCache.evict(articleId);
    publishedContentCache.evictPublishedList();
    removeFromEs(articleId);
}
```

- [ ] **Step 2: Add deletePermanently() method after hide()**

```java
@Transactional
public void deletePermanently(Long authorId, Long articleId) {
    Article a = articleMapper.selectById(articleId);
    if (a == null) {
        throw new BizException(4041, "文章不存在");
    }
    if (!a.getAuthorId().equals(authorId)) {
        throw new BizException(4031, "无权限");
    }
    // 先删正文，再删文章
    articleBodyMapper.deleteById(articleId);
    articleMapper.deleteById(articleId);
    publishedContentCache.evict(articleId);
    publishedContentCache.evictPublishedList();
    removeFromEs(articleId);
}
```

- [ ] **Step 3: Compile to verify no errors in these two methods**

Run: `cd OpenBlog-business && mvn compile -q 2>&1 | head -30`
Expected: Errors from remaining `ArticleStatus.DELETED` references in other methods of ArticleService (publish, detailMine, updateArticle)

- [ ] **Step 4: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/article/service/ArticleService.java
git commit -m "refactor(article): split unpublishOrDelete into hide() and deletePermanently()"
```

---

### Task 3: ArticleService — fix remaining status guards

**Files:**
- Modify: `OpenBlog-business/src/main/java/com/yqz/openblog/article/service/ArticleService.java`

Three methods need their `ArticleStatus.DELETED` guards updated.

- [ ] **Step 1: publish() — allow HIDDEN → PUBLISHED transition**

Replace line 366:
```java
// OLD:
if (a.getStatus() == ArticleStatus.DELETED) throw new BizException(4091, "当前文章不可发布");

// NEW:
// HIDDEN 状态允许重新发布；SCHEDULED 定时任务正常处理
```

Remove the DELETED guard entirely (HIDDEN is a valid pre-publish state):

In the `publish()` method, delete the line:
```java
if (a.getStatus() == ArticleStatus.DELETED) throw new BizException(4091, "当前文章不可发布");
```

- [ ] **Step 2: detailMine() — allow HIDDEN articles**

Replace lines 276-278:
```java
// OLD:
if (a.getStatus() == ArticleStatus.DELETED) {
    throw new BizException(4041, "文章不存在");
}

// NEW: remove these lines — HIDDEN articles are viewable by their author
```

Delete lines 276-278 entirely.

- [ ] **Step 3: updateArticle() — allow editing HIDDEN articles**

Replace lines 322-324:
```java
// OLD:
if (a.getStatus() == ArticleStatus.DELETED) {
    throw new BizException(4091, "当前文章不可编辑");
}

// NEW: remove these lines — HIDDEN articles are editable
```

Delete lines 322-324 entirely.

- [ ] **Step 4: Compile to verify no more DELETED references**

Run: `cd OpenBlog-business && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/article/service/ArticleService.java
git commit -m "fix(article): update status guards — allow HIDDEN as valid state"
```

---

### Task 4: ArticleService — include HIDDEN in listMine()

**Files:**
- Modify: `OpenBlog-business/src/main/java/com/yqz/openblog/article/service/ArticleService.java:440`

- [ ] **Step 1: Add HIDDEN to the visible statuses list**

Replace line 440:
```java
// OLD:
List<ArticleStatus> statuses = List.of(ArticleStatus.DRAFT, ArticleStatus.SCHEDULED, ArticleStatus.PUBLISHED);

// NEW:
List<ArticleStatus> statuses = List.of(ArticleStatus.DRAFT, ArticleStatus.SCHEDULED, ArticleStatus.PUBLISHED, ArticleStatus.HIDDEN);
```

- [ ] **Step 2: Compile to verify**

Run: `cd OpenBlog-business && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/article/service/ArticleService.java
git commit -m "feat(article): include HIDDEN status in listMine query"
```

---

### Task 5: ArticleController — wire new service methods

**Files:**
- Modify: `OpenBlog-business/src/main/java/com/yqz/openblog/article/controller/ArticleController.java:71-85`

- [ ] **Step 1: Update unpublish endpoint to call hide()**

Replace lines 71-76:
```java
// OLD:
@PostMapping("/articles/{articleId}/unpublish")
@PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
public ApiResponse<Void> unpublish(@PathVariable("articleId") Long articleId) {
    Long uid = currentUser.userId();
    articleService.unpublishOrDelete(uid, articleId);
    return ApiResponse.ok();
}

// NEW:
@PostMapping("/articles/{articleId}/unpublish")
@PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
public ApiResponse<Void> unpublish(@PathVariable("articleId") Long articleId) {
    Long uid = currentUser.userId();
    articleService.hide(uid, articleId);
    return ApiResponse.ok();
}
```

- [ ] **Step 2: Update DELETE endpoint to call deletePermanently()**

Replace lines 79-84:
```java
// OLD:
@DeleteMapping("/articles/{articleId}")
@PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
public ApiResponse<Void> deleteArticle(@PathVariable("articleId") Long articleId) {
    Long uid = currentUser.userId();
    articleService.unpublishOrDelete(uid, articleId);
    return ApiResponse.ok();
}

// NEW:
@DeleteMapping("/articles/{articleId}")
@PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
public ApiResponse<Void> deleteArticle(@PathVariable("articleId") Long articleId) {
    Long uid = currentUser.userId();
    articleService.deletePermanently(uid, articleId);
    return ApiResponse.ok();
}
```

- [ ] **Step 3: Compile to verify**

Run: `cd OpenBlog-business && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/article/controller/ArticleController.java
git commit -m "feat(article): wire hide() to unpublish endpoint, deletePermanently() to DELETE endpoint"
```

---

### Task 6: Frontend API — add unpublishArticle

**Files:**
- Modify: `vue/src/api/admin.js`

- [ ] **Step 1: Add unpublishArticle function**

After the `deleteMyArticle` function (after line 78), add:

```js
export function unpublishArticle(id) {
  return request(`/api/v1/articles/${id}/unpublish`, { method: 'POST', withAuth: true })
}
```

The `publishArticle` function already exists at line 64-66 and works correctly for re-publishing HIDDEN articles.

- [ ] **Step 2: Verify the file is valid**

Run: `cd vue && npx eslint vue/src/api/admin.js 2>&1 | tail -5`
Expected: No new errors (may have pre-existing warnings)

- [ ] **Step 3: Commit**

```bash
git add vue/src/api/admin.js
git commit -m "feat(admin): add unpublishArticle API function"
```

---

### Task 7: Frontend — ConsoleArticleManageView UI

**Files:**
- Modify: `vue/src/views/ConsoleArticleManageView.vue`

- [ ] **Step 1: Update imports to include publishArticle and unpublishArticle**

Replace line 46:
```js
// OLD:
import { deleteMyArticle, fetchMyArticles } from '../api/admin'

// NEW:
import { deleteMyArticle, fetchMyArticles, publishArticle, unpublishArticle } from '../api/admin'
```

- [ ] **Step 2: Add hide and publish handler functions**

After the `remove` function (after line 104), add:

```js
async function hideArticle(id) {
  articleError.value = ''
  articleSuccess.value = ''
  try {
    await unpublishArticle(id)
    await loadArticles()
    articleSuccess.value = '已隐藏'
  } catch (e) {
    articleError.value = e?.message || '隐藏失败'
    articleSuccess.value = ''
  }
}

async function republish(id) {
  articleError.value = ''
  articleSuccess.value = ''
  try {
    await publishArticle(id)
    await loadArticles()
    articleSuccess.value = '已发布'
  } catch (e) {
    articleError.value = e?.message || '发布失败'
    articleSuccess.value = ''
  }
}
```

- [ ] **Step 3: Update remove() to add confirm dialog**

Replace the `remove` function (lines 90-104):

```js
async function remove(id) {
  articleError.value = ''
  articleSuccess.value = ''
  if (!window.confirm('确定要永久删除这篇文章吗？此操作不可撤销。')) {
    return
  }
  try {
    await deleteMyArticle(id)
    await loadArticles()
    articleSuccess.value = '删除成功'
  } catch (e) {
    const apiCode = e?.code
    const httpStatus = e?.httpStatus
    const prefix = apiCode ? `错误码 ${apiCode}` : httpStatus ? `HTTP ${httpStatus}` : ''
    articleError.value = prefix ? `${prefix}：${e?.message || '删除失败'}` : e?.message || '删除失败'
    articleSuccess.value = ''
  }
}
```

- [ ] **Step 4: Update status display to show Chinese labels**

Replace the status display line 28:
```html
<!-- OLD: -->
<div class="manage-item-meta">{{ a.status }} · {{ formatDate(a.publishedAt) }}</div>

<!-- NEW: -->
<div class="manage-item-meta">{{ statusLabel(a.status) }} · {{ formatDate(a.publishedAt) }}</div>
```

Add the helper function after `formatDate` (after line 64):

```js
function statusLabel(s) {
  const map = { DRAFT: '草稿', SCHEDULED: '已预约', PUBLISHED: '已发布', HIDDEN: '已隐藏' }
  return map[s] || s
}
```

- [ ] **Step 5: Update action buttons in template**

Replace lines 30-33:
```html
<!-- OLD: -->
<div class="manage-item-actions" @click.stop>
  <button class="btn" style="padding: 6px 10px" @click="goEdit(a.id)">编辑</button>
  <button class="btn" style="padding: 6px 10px" @click="remove(a.id)">删除</button>
</div>

<!-- NEW: -->
<div class="manage-item-actions" @click.stop>
  <button class="btn" style="padding: 6px 10px" @click="goEdit(a.id)">编辑</button>
  <button v-if="a.status === 'PUBLISHED'" class="btn" style="padding: 6px 10px" @click="hideArticle(a.id)">隐藏</button>
  <button v-if="a.status === 'HIDDEN'" class="btn" style="padding: 6px 10px" @click="republish(a.id)">发布</button>
  <button class="btn" style="padding: 6px 10px" @click="remove(a.id)">删除</button>
</div>
```

- [ ] **Step 6: Verify build**

Run: `cd vue && npx vite build 2>&1 | tail -10`
Expected: Build succeeds

- [ ] **Step 7: Commit**

```bash
git add vue/src/views/ConsoleArticleManageView.vue
git commit -m "feat(article): add hide/publish buttons, delete confirm dialog, Chinese status labels"
```

---

### Task 8: Database migration — update existing DELETED articles

- [ ] **Step 1: Write migration SQL to change DELETED to HIDDEN**

```sql
UPDATE articles SET status = 'HIDDEN' WHERE status = 'DELETED';
```

Run manually (or via Flyway/Liquibase if the project uses migrations):
```bash
# Check current DELETED count
echo "SELECT COUNT(*) FROM articles WHERE status = 'DELETED';" | mysql -u <user> -p <database>
# Apply migration
echo "UPDATE articles SET status = 'HIDDEN' WHERE status = 'DELETED';" | mysql -u <user> -p <database>
```

- [ ] **Step 2: Compile full project to confirm end-to-end**

Run: `cd OpenBlog-business && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit (if migration file added to repo)**

```bash
git add <migration-file>
git commit -m "db: migrate DELETED articles to HIDDEN status"
```
