# 项目推荐功能设计文档

## 概述

在博客导航栏新增「项目推荐」入口，独立于文章系统，用于展示作者的项目作品。每个项目用 Markdown 撰写详细介绍，以独立数据库表存储，后台有独立管理页面。

## 导航栏改动

### 位置

在顶部导航栏的「文章」与「反馈」之间插入「项目推荐」按钮：

```
首页 → 文章 → 项目推荐 → 反馈 → 源码 → ...
```

### 样式调整

| 属性 | 原值 | 新值 |
|------|------|------|
| `.site-nav-link` font-size | `14px` | `15px` |
| `.site-nav` gap | `22px` | `28px` |
| 图标尺寸 | `16px` | `17px` |

在 `(max-width: 520px)` 断点下 gap 保持 `12px` 不变，不做字体增大。

## 数据库

新建 `projects` 表：

```sql
CREATE TABLE projects (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(120) NOT NULL COMMENT '项目名称',
    summary         VARCHAR(255)  COMMENT '简介',
    content_markdown TEXT         COMMENT 'Markdown 正文',
    cover_media_key VARCHAR(64)   COMMENT '封面图 media key',
    tech_stack      VARCHAR(255)  COMMENT '技术栈（逗号分隔）',
    project_url     VARCHAR(512)  COMMENT '项目链接',
    github_url      VARCHAR(512)  COMMENT '源码链接',
    sort_order      INT DEFAULT 0 COMMENT '排序值（越小越靠前）',
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED',
    published_at    DATETIME      COMMENT '发布时间',
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL,
    INDEX idx_projects_status_sort (status, sort_order, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 说明

- **无审核流程**：项目只有 DRAFT/PUBLISHED 两种状态，无需 review/workflow
- **无分类/标签表**：tech_stack 用逗号分隔字符串，前端显示为标签即可，不做独立关联表
- **无评论**：项目详情页不含评论区（如需可在后续迭代添加）
- **sort_order**：后台可拖拽或手动设置排序，列表按 sort_order ASC、published_at DESC 排列

## 后端

### 包结构

```
com.yqz.openblog.project
├── entity
│   └── Project.java
├── repo
│   └── ProjectRepository.java
├── dto
│   ├── ProjectListItemResponse.java
│   ├── ProjectDetailResponse.java
│   └── ProjectUpsertRequest.java
├── service
│   └── ProjectService.java
└── controller
    └── ProjectController.java
```

### 实体 `Project.java`

参照 `ChangelogEntry.java` 风格，使用 JPA + `@PrePersist`/`@PreUpdate`。字段完全对应 `projects` 表。

### Repository `ProjectRepository.java`

```java
public interface ProjectRepository extends JpaRepository<Project, Long> {
    Page<Project> findByStatusOrderBySortOrderAscPublishedAtDesc(
            ProjectStatus status, Pageable pageable);
}
```

### Service `ProjectService.java`

- `listPublished(page, size)` — 只查 PUBLISHED 状态，按 sort_order ASC, published_at DESC
- `detail(id)` — 查详情，无权限校验（公开）
- `listAll(page, size)` — 查全部（ADMIN）
- `create(req)` — 新建草稿
- `update(id, req)` — 更新
- `delete(id)` — 删除

### Controller `ProjectController.java`

路由前缀 `/api/v1/projects`，参照 `ChangelogController` 模式：

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/v1/projects` | 公开 | 列出已发布项目 |
| GET | `/api/v1/projects/{id}` | 公开 | 查看项目详情 |
| POST | `/api/v1/projects` | ADMIN | 新建 |
| PUT | `/api/v1/projects/{id}` | ADMIN | 更新 |
| DELETE | `/api/v1/projects/{id}` | ADMIN | 删除 |

### 状态枚举 `ProjectStatus`

```java
public enum ProjectStatus {
    DRAFT,
    PUBLISHED
}
```

## 前端

### 路由

在 `src/router/index.js` 新增：

```js
import ProjectsListView from '../views/ProjectsListView.vue'
import ProjectDetailView from '../views/ProjectDetailView.vue'

// 路由：
{ path: '/projects', name: 'projectsList', component: ProjectsListView }
{ path: '/project/:id', name: 'projectDetail', component: ProjectDetailView, props: true }
```

后台管理路由（ConsoleLayout 子路由）：

```js
{ path: 'projects', name: 'consoleProjects', component: ConsoleProjectsView }
```

### 页面：项目列表页 `/projects`

布局参考 `ChangelogListView.vue` 的简洁风格，改为卡片网格：

```
┌──────────────────────────────────────┐
│       项目推荐                         │
│       ×××××××××××××××××××××           │
│                                       │
│  ┌──────┐  ┌──────┐  ┌──────┐        │
│  │封面图│  │封面图│  │封面图│        │
│  │标题  │  │标题  │  │标题  │        │
│  │技术栈│  │技术栈│  │技术栈│        │
│  └──────┘  └──────┘  └──────┘        │
│                                       │
│  ┌──────┐  ┌──────┐  ┌──────┐        │
│  │ ...  │  │ ...  │  │ ...  │        │
│  └──────┘  └──────┘  └──────┘        │
└──────────────────────────────────────┘
```

- 每张卡片：封面图（16:9 比例）、项目名称、简介、技术栈标签
- 点击卡片进入详情页
- 空状态：显示"暂无项目"

### 页面：项目详情页 `/project/:id`

结构接近 `ChangelogDetailView.vue`：

```
┌────────────────────────────────────────┐
│  ← 返回项目列表                         │
│                                       │
│  项目名称                               │
│  发布时间 · 技术栈标签                   │
│                                       │
│  [封面大图]                             │
│                                       │
│  [Markdown 正文]                       │
│                                       │
│  [项目链接] [GitHub 链接]               │
└────────────────────────────────────────┘
```

- 复用 markdown-body 样式
- 底部显示项目链接按钮和 GitHub 链接按钮
- 空/错误状态：显示"项目不存在"

### 页面：后台项目管理 `/console/projects`

参照 `ConsoleChangelogView.vue` 或文章管理风格：

```
┌── 项目管理 ──────────────────────────────┐
│  [+ 新建项目]                            │
│                                         │
│  ┌─ 项目列表 ──────────────────────────┐ │
│  │ 封面 | 标题 | 状态 | 排序 | 操作     │ │
│  ├─────────────────────────────────────┤ │
│  │ ...                                 │ │
│  └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

新建/编辑表单字段：
- 项目名称（必填）
- 简介（选填）
- Markdown 正文（必填，用 textarea）
- 封面图（media picker / 输入 media key）
- 技术栈（逗号分隔输入）
- 项目链接、GitHub 链接（选填）
- 排序值（数字）
- 状态（DRAFT / PUBLISHED）

### API 层

新建 `src/api/project.js`：

```js
import { request } from './http'

export function fetchProjects(page = 0, size = 20) {
  return request(`/api/v1/projects?page=${page}&size=${size}`)
}

export function fetchProjectDetail(id) {
  return request(`/api/v1/projects/${id}`)
}

export function createProject(payload) {
  return request('/api/v1/projects', {
    method: 'POST', withAuth: true,
    body: JSON.stringify(payload)
  })
}

export function updateProject(id, payload) {
  return request(`/api/v1/projects/${id}`, {
    method: 'PUT', withAuth: true,
    body: JSON.stringify(payload)
  })
}

export function deleteProject(id) {
  return request(`/api/v1/projects/${id}`, {
    method: 'DELETE', withAuth: true
  })
}
```

### 导航栏（BlogHeader.vue）

在「文章」和「反馈」之间插入：

```html
<router-link to="/projects" class="site-nav-link" active-class="active">
  <span class="site-nav-ico" aria-hidden="true">
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <rect x="3" y="3" width="18" height="18" rx="2" />
      <path d="M9 3v18" />
      <path d="M3 9h18" />
    </svg>
  </span>
  项目推荐
</router-link>
```

图标使用网格/应用图标（四宫格）表示"项目"。

## 实现顺序

1. 创建 `projects` 表 SQL
2. 后端：Project 实体、枚举、Repository
3. 后端：DTOs、Service、Controller
4. 前端：API 层（project.js）
5. 前端：项目列表页 ProjectsListView
6. 前端：项目详情页 ProjectDetailView
7. 前端：后台项目管理页 ConsoleProjectsView
8. 前端：导航栏添加「项目推荐」按钮 + 样式调整
9. 前端：路由注册
10. 验证：编译、启动、浏览

## 未纳入范围

- 项目评论功能
- 项目分类/多标签系统
- 拖拽排序（后续可基于 sort_order 加 UI）
- 定时发布
