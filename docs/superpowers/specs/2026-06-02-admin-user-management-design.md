# Admin User Management — Design Spec

**Date**: 2026-06-02  
**Branch**: `feature/admin-user-management`  
**Status**: Draft

---

## Overview

后台用户管理功能扩展：管理员可查看用户列表（搜索/筛选）、用户详情、封禁/限制互动/解封、变更角色。

---

## Backend Design

### 1. User Status Model

四种状态及其流转：

```
PENDING  ──批准──→  ACTIVE  ──封禁──→  BANNED
                   ACTIVE  ──限制──→  RESTRICTED
BANNED     ──解封──→  ACTIVE
RESTRICTED ──解除──→  ACTIVE
```

| 状态 | 含义 | 可登录 | 可互动 |
|------|------|--------|--------|
| `ACTIVE` | 正常 | ✓ | ✓ |
| `RESTRICTED` | 限制互动（小黑屋） | ✓ | ✗ |
| `BANNED` | 封禁 | ✗ | ✗ |
| `PENDING` | 待审核 | ✗ | ✗ |

### 2. New/Modified DTOs

**`ChangeRoleRequest`（新增）**
```java
// user/dto/ChangeRoleRequest.java
@Data
public class ChangeRoleRequest {
    @NotBlank
    private String role; // ADMIN, AUTHOR, READER
}
```

**`UserDetailResponse`（新增）**
```java
// user/dto/UserDetailResponse.java
@Data
public class UserDetailResponse {
    private Long userId;
    private String username;
    private String email;
    private String avatarUrl;
    private String bio;
    private String role;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private long articleCount;
    private long commentCount;
}
```

**`ChangeStatusRequest`（已有，值域扩展）**

现有 DTO 值域从 ACTIVE/BANNED 扩展为 ACTIVE/BANNED/RESTRICTED。

**`AdminUserListItemResponse`（已有，无需改动）**

### 3. API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/admin/users?keyword=&status=&role=&page=&size=` | ADMIN | 用户列表（增强：搜索关键词 + 状态筛选 + 角色筛选） |
| `GET` | `/api/v1/admin/users/{userId}` | ADMIN | 用户详情（基本信息 + 文章数 + 评论数） |
| `PUT` | `/api/v1/admin/users/{userId}/status` | ADMIN | 修改用户状态 |
| `PUT` | `/api/v1/admin/users/{userId}/role` | ADMIN | 修改用户角色 |

**权限**：所有管理端点 `@PreAuthorize("hasRole('ADMIN')")`。AUTHOR 不可调用写入端点。

**业务规则**：
- 不能修改自己的角色
- 不能封禁/限制其他 ADMIN
- 状态变更需校验合法性（如不能从 PENDING 直接变 RESTRICTED）
- PENDING 用户只能批准为 ACTIVE 或拒绝（删除）
- BANNED/RESTRICTED 只能恢复为 ACTIVE

### 4. Service Layer

在 `UserAdminService` 中新增方法（新建类，遵循项目 MyBatis-Plus 模式）：

| Method | Description |
|--------|-------------|
| `getUserDetail(Long userId)` | 查询用户基本信息，关联统计 article 和 comment 数量 |
| `changeStatus(Long userId, String newStatus)` | 校验后更新 status |
| `changeRole(Long targetUserId, String newRole)` | 校验后更新 role |
| `listUsers(String keyword, String status, String role, int page, int size)` | 增强分页查询，支持 LIKE 搜索 username/email，状态和角色精确筛选 |

现有 `approveReader` 方法保留在 `AuthService` 中不动。

### 5. Interaction Interception

以下 Service 的写入方法增加 RESTRICTED 检查：

| Service | Method | 拦截逻辑 |
|---------|--------|---------|
| `CommentService` | `createComment()` | 若当前用户 status=RESTRICTED，抛 `BusinessException(403, "账号已被限制互动")` |
| `InteractionService` | `toggleLike()`, `toggleBookmark()`, `toggleFollow()` | 同上 |

实现方式：在 `CurrentUser` 工具类或 Service 基类中提供一个 `checkNotRestricted()` 方法，各 Service 调用。

### 6. Affected Files (Backend)

```
OpenBlog-business/src/main/java/com/yqz/openblog/
├── user/
│   ├── dto/
│   │   ├── ChangeRoleRequest.java          (新增)
│   │   └── UserDetailResponse.java         (新增)
│   ├── service/
│   │   └── UserAdminService.java           (新增)
│   └── controller/
│       └── UserAdminController.java        (修改：新增端点 + 增强列表查询)
├── comment/service/
│   └── CommentService.java                 (修改：RESTRICTED 拦截)
├── interaction/service/
│   └── InteractionService.java             (修改：RESTRICTED 拦截)
└── config/
    └── SecurityConfig.java                 (修改：调整 admin 路径权限)
```

---

## Frontend Design

### 1. Routes

```
/console/users              → ConsoleUsersView.vue       (已有，增强)
/console/users/:userId      → ConsoleUserDetailView.vue  (新增)
```

### 2. ConsoleUsersView（增强）

**搜索/筛选栏**（新增）：
- 关键词输入框（搜索 username / email）
- 状态下拉：全部 / 活跃 / 封禁 / 限制互动 / 待审核
- 角色下拉：全部 / 管理员 / 作者 / 读者
- 搜索按钮 + 重置按钮

**表格增强**：
- 角色列使用彩色标签（ADMIN 红、AUTHOR 蓝、READER 绿）
- 状态列使用彩色标签（ACTIVE 绿、BANNED 红、RESTRICTED 橙、PENDING 黄）
- 新增操作列（仅 ADMIN 可见）：
  - 角色变更下拉按钮
  - 状态变更按钮组（根据当前状态动态显示可用操作）
- 点击用户名跳转详情页

### 3. ConsoleUserDetailView（新增）

**面包屑**：`用户管理 > {username}`

**布局**：两栏（左信息卡 + 右统计卡）

**左栏 — 用户信息卡**：
- 大头像 + 用户名 + 角色标签 + 状态标签
- 邮箱（可复制）
- 个人简介

**右栏 — 统计卡片**：
- 文章数、评论数
- 注册时间、最近更新时间

**操作面板**（仅 ADMIN 可见，底部固定）：
- 角色变更下拉按钮组
- 状态操作按钮组（根据当前状态动态显示允许的操作）
- 操作确认弹窗（避免误操作）

### 4. API Module

`vue/src/api/userAdmin.js` 新增函数：

```js
fetchAdminUsers({ keyword, status, role, page, size })  // 增强
fetchUserDetail(userId)                                  // 新增
changeUserStatus(userId, status)                         // 新增
changeUserRole(userId, role)                             // 新增
```

### 5. UI Style

沿用项目现有简约卡片风格 — 参考 `ConsoleSiteConfigView.vue` 的表单布局和 `ConsoleLayout.vue` 的角色标签样式。

- 角色标签配色：ADMIN（红色系）、AUTHOR（蓝色系）、READER（灰绿色系）
- 状态标签配色：ACTIVE（绿色）、BANNED（红色）、RESTRICTED（橙色）、PENDING（黄色）
- 操作按钮：小型 `outline` 风格，危险操作（封禁/限制）有二次确认弹窗
- 响应式：移动端操作列折叠为下拉菜单

### 6. Affected Files (Frontend)

```
vue/src/
├── api/
│   └── userAdmin.js                      (修改：新增 API 函数)
├── views/
│   ├── ConsoleUsersView.vue              (修改：搜索筛选 + 操作列)
│   └── ConsoleUserDetailView.vue         (新增：用户详情页)
├── router/
│   └── index.js                          (修改：新增详情路由)
└── layouts/
    └── ConsoleLayout.vue                 (修改：侧边栏保持已有"用户列表"入口，无需改动)
```

---

## Acceptance Criteria

1. ADMIN 登录后可在用户列表页搜索/筛选用户
2. ADMIN 点击用户可查看详情（含文章数、评论数等统计）
3. ADMIN 可封禁用户（ACTIVE → BANNED），被禁用户无法登录
4. ADMIN 可限制互动（ACTIVE → RESTRICTED），被限用户可登录但不可评论/点赞/收藏/关注
5. ADMIN 可解封/解除限制（BANNED/RESTRICTED → ACTIVE）
6. ADMIN 可变更用户角色（AUTHOR ↔ READER，READER → ADMIN 等）
7. ADMIN 不能修改自己的角色，不能封禁其他 ADMIN
8. AUTHOR 进入用户管理页面只可查看，无操作权限
9. RESTRICTED 用户进行互动操作时收到明确的 403 提示
10. 所有危险操作（封禁/限制/角色变更）有二次确认
