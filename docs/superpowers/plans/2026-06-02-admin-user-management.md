# Admin User Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the admin console with user status management (ban/restrict/unban), role management, user detail view, search/filter on the user list, and RESTRICTED-user interaction blocking.

**Architecture:** Backend adds new endpoints to `UserAdminController`, a new `UserAdminService` for business logic, and modifies `AuthService`/`CommentService`/`InteractionService` to support the RESTRICTED status. Frontend adds a user detail page, enhances the user list with search/filter/action buttons, and extends the API module.

**Tech Stack:** Java 17 + Spring Boot 3.5 + MyBatis-Plus + Vue 3 + Vue Router 4

---

### Task 1: Backend — New DTOs (ChangeRoleRequest, UserDetailResponse)

**Files:**
- Create: `OpenBlog-business/src/main/java/com/yqz/openblog/user/dto/ChangeRoleRequest.java`
- Create: `OpenBlog-business/src/main/java/com/yqz/openblog/user/dto/UserDetailResponse.java`

- [ ] **Step 1: Create ChangeRoleRequest.java**

```java
package com.yqz.openblog.user.dto;

import jakarta.validation.constraints.NotBlank;

public class ChangeRoleRequest {

    @NotBlank
    private String role; // ADMIN, AUTHOR, READER

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
```

- [ ] **Step 2: Create UserDetailResponse.java**

```java
package com.yqz.openblog.user.dto;

import java.time.Instant;

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

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getArticleCount() { return articleCount; }
    public void setArticleCount(long articleCount) { this.articleCount = articleCount; }
    public long getCommentCount() { return commentCount; }
    public void setCommentCount(long commentCount) { this.commentCount = commentCount; }
}
```

- [ ] **Step 3: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/user/dto/ChangeRoleRequest.java OpenBlog-business/src/main/java/com/yqz/openblog/user/dto/UserDetailResponse.java
git commit -m "feat: add ChangeRoleRequest and UserDetailResponse DTOs"
```

---

### Task 2: Backend — Update ChangeStatusRequest comment for RESTRICTED

**Files:**
- Modify: `OpenBlog-business/src/main/java/com/yqz/openblog/user/dto/ChangeStatusRequest.java`

- [ ] **Step 1: Update the comment to reflect expanded value domain**

Edit `ChangeStatusRequest.java`, change the comment on the `status` field from `// ACTIVE/BANNED` to `// ACTIVE/BANNED/RESTRICTED`:

```java
package com.yqz.openblog.user.dto;

import jakarta.validation.constraints.NotNull;

public class ChangeStatusRequest {
    @NotNull
    private String status; // ACTIVE / BANNED / RESTRICTED

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/user/dto/ChangeStatusRequest.java
git commit -m "feat: expand ChangeStatusRequest value domain to include RESTRICTED"
```

---

### Task 3: Backend — UserAdminService (new service class)

**Files:**
- Create: `OpenBlog-business/src/main/java/com/yqz/openblog/user/service/UserAdminService.java`

- [ ] **Step 1: Create UserAdminService.java**

```java
package com.yqz.openblog.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yqz.openblog.article.entity.Article;
import com.yqz.openblog.article.entity.ArticleStatus;
import com.yqz.openblog.article.repo.ArticleMapper;
import com.yqz.openblog.comment.entity.Comment;
import com.yqz.openblog.comment.entity.CommentStatus;
import com.yqz.openblog.comment.repo.CommentMapper;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.user.entity.User;
import com.yqz.openblog.user.entity.UserRole;
import com.yqz.openblog.user.repo.UserMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class UserAdminService {

    private final UserMapper userMapper;
    private final ArticleMapper articleMapper;
    private final CommentMapper commentMapper;

    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "BANNED", "RESTRICTED");
    private static final Set<String> RECOVERABLE_STATUSES = Set.of("BANNED", "RESTRICTED");

    public UserAdminService(UserMapper userMapper, ArticleMapper articleMapper, CommentMapper commentMapper) {
        this.userMapper = userMapper;
        this.articleMapper = articleMapper;
        this.commentMapper = commentMapper;
    }

    /**
     * 增强分页列表：支持关键词搜索 username/email，状态和角色精确筛选。
     */
    public IPage<User> listUsers(String keyword, String status, String role, int page, int size) {
        Page<User> mpPage = new Page<>(page + 1L, size);
        LambdaQueryWrapper<User> w = Wrappers.lambdaQuery(User.class);

        if (keyword != null && !keyword.isBlank()) {
            w.and(wr -> wr.like(User::getUsername, keyword.trim()).or().like(User::getEmail, keyword.trim()));
        }
        if (status != null && !status.isBlank()) {
            w.eq(User::getStatus, status.trim());
        }
        if (role != null && !role.isBlank()) {
            try {
                UserRole r = UserRole.valueOf(role.trim().toUpperCase());
                w.eq(User::getRole, r);
            } catch (IllegalArgumentException ignored) {
                // ignore invalid role filter
            }
        }
        w.orderByDesc(User::getCreatedAt);
        return userMapper.selectPage(mpPage, w);
    }

    /**
     * 用户详情：基本信息 + 文章数 + 评论数。
     */
    public com.yqz.openblog.user.dto.UserDetailResponse getUserDetail(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BizException(4041, "用户不存在");
        }

        long articleCount = articleMapper.selectCount(
                Wrappers.lambdaQuery(Article.class).eq(Article::getAuthorId, userId));
        long commentCount = commentMapper.selectCount(
                Wrappers.lambdaQuery(Comment.class).eq(Comment::getUserId, userId));

        com.yqz.openblog.user.dto.UserDetailResponse r = new com.yqz.openblog.user.dto.UserDetailResponse();
        r.setUserId(u.getId());
        r.setUsername(u.getUsername());
        r.setEmail(u.getEmail());
        r.setAvatarUrl(u.getAvatarUrl());
        r.setBio(u.getBio());
        r.setRole(u.getRole().name());
        r.setStatus(u.getStatus());
        r.setCreatedAt(u.getCreatedAt());
        r.setUpdatedAt(u.getUpdatedAt());
        r.setArticleCount(articleCount);
        r.setCommentCount(commentCount);
        return r;
    }

    /**
     * 变更用户状态。
     * 规则：
     * - 不能修改自己的状态
     * - 不能封禁/限制其他 ADMIN
     * - PENDING 只能通过 approve 变为 ACTIVE，不在此方法处理
     * - BANNED/RESTRICTED 只能恢复为 ACTIVE
     * - ACTIVE 可以变为 BANNED 或 RESTRICTED
     */
    public void changeStatus(Long callerUserId, Long targetUserId, String newStatus) {
        if (callerUserId.equals(targetUserId)) {
            throw new BizException(4003, "不能修改自己的状态");
        }
        if (!ALLOWED_STATUSES.contains(newStatus)) {
            throw new BizException(4000, "无效的状态值，允许: ACTIVE / BANNED / RESTRICTED");
        }
        User u = userMapper.selectById(targetUserId);
        if (u == null) {
            throw new BizException(4041, "用户不存在");
        }
        if (u.getRole() == UserRole.ADMIN) {
            throw new BizException(4003, "不能修改管理员的账号状态");
        }
        if ("PENDING".equals(u.getStatus())) {
            throw new BizException(4003, "待审核用户请使用审核接口");
        }
        // BANNED or RESTRICTED → ACTIVE
        if (RECOVERABLE_STATUSES.contains(u.getStatus())) {
            if (!"ACTIVE".equals(newStatus)) {
                throw new BizException(4003, "当前状态只能恢复为 ACTIVE");
            }
        }
        u.setStatus(newStatus);
        userMapper.updateById(u);
    }

    /**
     * 变更用户角色。
     * 规则：
     * - 不能修改自己的角色
     * - 不能修改其他 ADMIN 的角色
     */
    public void changeRole(Long callerUserId, Long targetUserId, String newRoleStr) {
        if (callerUserId.equals(targetUserId)) {
            throw new BizException(4003, "不能修改自己的角色");
        }
        UserRole newRole;
        try {
            newRole = UserRole.valueOf(newRoleStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(4000, "无效的角色值，允许: ADMIN / AUTHOR / READER");
        }
        User u = userMapper.selectById(targetUserId);
        if (u == null) {
            throw new BizException(4041, "用户不存在");
        }
        if (u.getRole() == UserRole.ADMIN) {
            throw new BizException(4003, "不能修改管理员的角色");
        }
        u.setRole(newRole);
        userMapper.updateById(u);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/user/service/UserAdminService.java
git commit -m "feat: add UserAdminService with search/filter, detail, status & role management"
```

---

### Task 4: Backend — Extend UserAdminController with new endpoints

**Files:**
- Modify: `OpenBlog-business/src/main/java/com/yqz/openblog/user/controller/UserAdminController.java`

- [ ] **Step 1: Replace UserAdminController with enhanced version**

Replace the entire file content:

```java
package com.yqz.openblog.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.security.CurrentUser;
import com.yqz.openblog.user.dto.AdminUserListItemResponse;
import com.yqz.openblog.user.dto.ChangeRoleRequest;
import com.yqz.openblog.user.dto.ChangeStatusRequest;
import com.yqz.openblog.user.dto.PendingUserResponse;
import com.yqz.openblog.user.dto.UserDetailResponse;
import com.yqz.openblog.user.entity.User;
import com.yqz.openblog.user.entity.UserRole;
import com.yqz.openblog.user.repo.UserMapper;
import com.yqz.openblog.user.service.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class UserAdminController {

    private final UserMapper userMapper;
    private final UserAdminService userAdminService;
    private final CurrentUser currentUser;

    public UserAdminController(UserMapper userMapper, UserAdminService userAdminService, CurrentUser currentUser) {
        this.userMapper = userMapper;
        this.userAdminService = userAdminService;
        this.currentUser = currentUser;
    }

    /** 用户列表（增强：搜索关键词 + 状态筛选 + 角色筛选） */
    @GetMapping("/admin/users")
    @PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
    public ApiResponse<PageResult<AdminUserListItemResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role
    ) {
        IPage<User> mpPage = userAdminService.listUsers(keyword, status, role, page, size);
        List<AdminUserListItemResponse> items = mpPage.getRecords().stream().map(this::toListItem).toList();
        return ApiResponse.ok(new PageResult<>(items, page, size, mpPage.getTotal()));
    }

    /** 用户详情 */
    @GetMapping("/admin/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserDetailResponse> getUserDetail(@PathVariable("userId") Long userId) {
        return ApiResponse.ok(userAdminService.getUserDetail(userId));
    }

    /** 修改用户状态 */
    @PutMapping("/admin/users/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> changeStatus(@PathVariable("userId") Long userId,
                                          @Valid @RequestBody ChangeStatusRequest req) {
        Long callerId = currentUser.userId();
        if (callerId == null) {
            throw new BizException(4010, "未登录");
        }
        userAdminService.changeStatus(callerId, userId, req.getStatus());
        return ApiResponse.ok();
    }

    /** 修改用户角色 */
    @PutMapping("/admin/users/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> changeRole(@PathVariable("userId") Long userId,
                                        @Valid @RequestBody ChangeRoleRequest req) {
        Long callerId = currentUser.userId();
        if (callerId == null) {
            throw new BizException(4010, "未登录");
        }
        userAdminService.changeRole(callerId, userId, req.getRole());
        return ApiResponse.ok();
    }

    /** 待审核读者列表（保留已有功能） */
    @GetMapping("/admin/users/pending")
    @PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
    public ApiResponse<List<PendingUserResponse>> listPendingReaders() {
        List<User> users = userMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(User.class)
                        .eq(User::getStatus, "PENDING")
                        .eq(User::getRole, UserRole.READER)
                        .orderByDesc(User::getCreatedAt));
        List<PendingUserResponse> list = users.stream().map(this::toPending).toList();
        return ApiResponse.ok(list);
    }

    /** 审核通过读者 */
    @PostMapping("/admin/users/{userId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
    public ApiResponse<Void> approveReader(@PathVariable("userId") Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BizException(4041, "用户不存在");
        }
        if (u.getRole() != UserRole.READER) {
            throw new BizException(4000, "仅支持审核读者账号");
        }
        if (!"PENDING".equals(u.getStatus())) {
            throw new BizException(4000, "该账号不在待审核状态");
        }
        u.setStatus("ACTIVE");
        userMapper.updateById(u);
        return ApiResponse.ok();
    }

    private PendingUserResponse toPending(User u) {
        PendingUserResponse r = new PendingUserResponse();
        r.setUserId(u.getId());
        r.setUsername(u.getUsername());
        r.setEmail(u.getEmail());
        r.setCreatedAt(u.getCreatedAt());
        return r;
    }

    private AdminUserListItemResponse toListItem(User u) {
        AdminUserListItemResponse r = new AdminUserListItemResponse();
        r.setUserId(u.getId());
        r.setUsername(u.getUsername());
        r.setDisplayName(resolveDisplayName(u));
        r.setEmail(u.getEmail());
        r.setAvatarUrl(u.getAvatarUrl());
        r.setRole(u.getRole());
        r.setStatus(u.getStatus());
        r.setCreatedAt(u.getCreatedAt());
        return r;
    }

    private static String resolveDisplayName(User u) {
        String n = u.getNickname();
        if (n != null && !n.isBlank()) {
            return n.trim();
        }
        return u.getUsername();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/user/controller/UserAdminController.java
git commit -m "feat: add user detail, status change, and role change admin endpoints"
```

---

### Task 5: Backend — Update AuthService to allow RESTRICTED users to log in

**Files:**
- Modify: `OpenBlog-business/src/main/java/com/yqz/openblog/user/service/AuthService.java`

- [ ] **Step 1: Change login status check (line ~102-107)**

Find the block in the `login` method:
```java
        if ("PENDING".equals(user.getStatus())) {
            throw new BizException(4014, "账号待管理员审核通过后方可登录");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            loginLockoutService.recordPasswordFailure(ipSeg);
            throw new BizException(4011, "账号已被封禁");
        }
```

Replace with:
```java
        if ("PENDING".equals(user.getStatus())) {
            throw new BizException(4014, "账号待管理员审核通过后方可登录");
        }
        if ("BANNED".equals(user.getStatus())) {
            loginLockoutService.recordPasswordFailure(ipSeg);
            throw new BizException(4011, "账号已被封禁");
        }
```

- [ ] **Step 2: Change assertUserAccountActive method (line ~254-260)**

Find:
```java
    private void assertUserAccountActive(User user) {
        if ("PENDING".equals(user.getStatus())) {
            throw new BizException(4014, "账号待管理员审核通过后方可登录");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException(4011, "账号已被封禁");
        }
    }
```

Replace with:
```java
    private void assertUserAccountActive(User user) {
        if ("PENDING".equals(user.getStatus())) {
            throw new BizException(4014, "账号待管理员审核通过后方可登录");
        }
        if ("BANNED".equals(user.getStatus())) {
            throw new BizException(4011, "账号已被封禁");
        }
    }
```

- [ ] **Step 3: Change changePassword status check (line ~192)**

Find:
```java
        if ("BANNED".equals(user.getStatus())) {
            throw new BizException(4011, "账号已被封禁");
        }
```

Keep as-is — BANNED users still cannot change password. RESTRICTED users can. This is correct.

- [ ] **Step 4: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/user/service/AuthService.java
git commit -m "feat: allow RESTRICTED users to login and refresh tokens"
```

---

### Task 6: Backend — RESTRICTED interception in CommentService and InteractionService

**Files:**
- Modify: `OpenBlog-business/src/main/java/com/yqz/openblog/comment/service/CommentService.java`
- Modify: `OpenBlog-business/src/main/java/com/yqz/openblog/interaction/service/InteractionService.java`

- [ ] **Step 1: Update CommentService.ensureUserActive to also reject RESTRICTED**

In `CommentService.java`, find `ensureUserActive` method (~line 270):
```java
    private void ensureUserActive(Long uid) {
        User u = userRepository.findById(uid).orElseThrow(() -> new BizException(4041, "用户不存在"));
        if (!"ACTIVE".equals(u.getStatus())) {
            throw new BizException(4011, "账号已被封禁");
        }
    }
```

Replace with:
```java
    private void ensureUserActive(Long uid) {
        User u = userRepository.findById(uid).orElseThrow(() -> new BizException(4041, "用户不存在"));
        if ("BANNED".equals(u.getStatus())) {
            throw new BizException(4011, "账号已被封禁");
        }
        if ("RESTRICTED".equals(u.getStatus())) {
            throw new BizException(4012, "账号已被限制互动");
        }
    }
```

- [ ] **Step 2: Update InteractionService.ensureUserActive to also reject RESTRICTED**

In `InteractionService.java`, find `ensureUserActive` method (~line 36):
```java
    private void ensureUserActive(Long uid) {
        User u = userMapper.selectById(uid);
        if (u == null) {
            throw new BizException(4041, "用户不存在");
        }
        if (!"ACTIVE".equals(u.getStatus())) {
            throw new BizException(4011, "账号已被封禁");
        }
    }
```

Replace with:
```java
    private void ensureUserActive(Long uid) {
        User u = userMapper.selectById(uid);
        if (u == null) {
            throw new BizException(4041, "用户不存在");
        }
        if ("BANNED".equals(u.getStatus())) {
            throw new BizException(4011, "账号已被封禁");
        }
        if ("RESTRICTED".equals(u.getStatus())) {
            throw new BizException(4012, "账号已被限制互动");
        }
    }
```

- [ ] **Step 3: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/comment/service/CommentService.java OpenBlog-business/src/main/java/com/yqz/openblog/interaction/service/InteractionService.java
git commit -m "feat: block RESTRICTED users from commenting, liking, bookmarking, and following"
```

---

### Task 7: Frontend — Extend userAdmin.js API module

**Files:**
- Modify: `vue/src/api/userAdmin.js`

- [ ] **Step 1: Replace userAdmin.js with enhanced version**

```js
import { request } from './http'

export function fetchAdminUsers({ keyword, status, role, page = 0, size = 24 } = {}) {
  const params = new URLSearchParams()
  params.set('page', String(page))
  params.set('size', String(size))
  if (keyword) params.set('keyword', keyword)
  if (status) params.set('status', status)
  if (role) params.set('role', role)
  return request(`/api/v1/admin/users?${params.toString()}`, { method: 'GET', withAuth: true })
}

export function fetchPendingReaders() {
  return request('/api/v1/admin/users/pending', { method: 'GET', withAuth: true })
}

export function approveReader(userId) {
  return request(`/api/v1/admin/users/${userId}/approve`, { method: 'POST', withAuth: true })
}

export function fetchUserDetail(userId) {
  return request(`/api/v1/admin/users/${userId}`, { method: 'GET', withAuth: true })
}

export function changeUserStatus(userId, status) {
  return request(`/api/v1/admin/users/${userId}/status`, {
    method: 'PUT',
    withAuth: true,
    body: JSON.stringify({ status })
  })
}

export function changeUserRole(userId, role) {
  return request(`/api/v1/admin/users/${userId}/role`, {
    method: 'PUT',
    withAuth: true,
    body: JSON.stringify({ role })
  })
}
```

- [ ] **Step 2: Commit**

```bash
git add vue/src/api/userAdmin.js
git commit -m "feat: add user detail, status change, and role change API functions"
```

---

### Task 8: Frontend — Enhance ConsoleUsersView with search/filter and action buttons

**Files:**
- Modify: `vue/src/views/ConsoleUsersView.vue`

- [ ] **Step 1: Replace ConsoleUsersView.vue with enhanced version**

```vue
<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <h1>用户列表</h1>
      </div>
      <p class="console-prose" style="margin-top: 6px">
        管理注册用户：搜索、筛选、封禁、限制互动、变更角色。
      </p>
    </header>

    <div class="console-card" style="margin-bottom: 16px">
      <div class="admin-user-filters">
        <input
          v-model="filters.keyword"
          type="text"
          class="form-input"
          placeholder="搜索用户名或邮箱…"
          style="flex: 1; min-width: 180px"
          @keyup.enter="applySearch"
        />
        <select v-model="filters.status" class="form-input" style="width: 130px">
          <option value="">全部状态</option>
          <option value="ACTIVE">活跃</option>
          <option value="BANNED">封禁</option>
          <option value="RESTRICTED">限制互动</option>
          <option value="PENDING">待审核</option>
        </select>
        <select v-model="filters.role" class="form-input" style="width: 110px">
          <option value="">全部角色</option>
          <option value="ADMIN">管理员</option>
          <option value="AUTHOR">作者</option>
          <option value="READER">读者</option>
        </select>
        <button type="button" class="btn btn-primary" @click="applySearch">搜索</button>
        <button type="button" class="btn" @click="resetFilters">重置</button>
      </div>
    </div>

    <div class="console-card console-inner-card">
      <div v-if="loading && users.length === 0" style="color: var(--console-muted, var(--muted))">加载中...</div>
      <template v-else-if="!canAccess">
        <div style="color: var(--console-muted, var(--muted)); font-size: 14px">当前账号无权限查看用户列表。</div>
      </template>
      <template v-else>
        <div v-if="users.length === 0" style="color: var(--console-muted, var(--muted)); font-size: 14px">
          暂无匹配的用户
        </div>
        <div v-else class="admin-user-table-wrap">
          <table class="admin-user-table">
            <thead>
              <tr>
                <th>用户</th>
                <th>邮箱</th>
                <th>角色</th>
                <th>状态</th>
                <th>注册时间</th>
                <th v-if="isAdmin">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="u in users" :key="u.userId">
                <td>
                  <router-link :to="`/console/users/${u.userId}`" class="admin-user-link">
                    <img
                      class="admin-user-cell-avatar"
                      :src="u.avatarUrl || defaultAvatar"
                      :alt="u.displayName || u.username || ''"
                    />
                    <span>{{ u.displayName || u.username || '—' }}</span>
                  </router-link>
                </td>
                <td class="admin-user-cell-muted">{{ u.email || '—' }}</td>
                <td>
                  <span class="admin-tag" :class="'tag-role-' + (u.role || '').toLowerCase()">
                    {{ roleLabel(u.role) }}
                  </span>
                </td>
                <td>
                  <span class="admin-tag" :class="'tag-status-' + (u.status || '').toLowerCase()">
                    {{ statusLabel(u.status) }}
                  </span>
                </td>
                <td class="admin-user-cell-muted">{{ formatTime(u.createdAt) }}</td>
                <td v-if="isAdmin">
                  <div class="admin-user-actions">
                    <select
                      class="form-input form-input-sm"
                      :value="u.role"
                      @change="handleRoleChange(u, ($event.target).value)"
                    >
                      <option value="ADMIN">管理员</option>
                      <option value="AUTHOR">作者</option>
                      <option value="READER">读者</option>
                    </select>
                    <template v-if="u.status === 'ACTIVE'">
                      <button type="button" class="btn btn-sm btn-outline-danger" @click="handleBan(u)">封禁</button>
                      <button type="button" class="btn btn-sm btn-outline-warning" @click="handleRestrict(u)">限制互动</button>
                    </template>
                    <template v-else-if="u.status === 'BANNED' || u.status === 'RESTRICTED'">
                      <button type="button" class="btn btn-sm btn-outline-success" @click="handleUnban(u)">解封</button>
                    </template>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="hasMore" style="margin-top: 16px; text-align: center">
          <button type="button" class="btn" :disabled="loadingMore" @click="loadMore">
            {{ loadingMore ? '加载中…' : '加载更多' }}
          </button>
        </div>
        <div v-if="error" class="error" style="margin-top: 14px">{{ error }}</div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchMe } from '../api/admin'
import { fetchAdminUsers, changeUserStatus, changeUserRole } from '../api/userAdmin'

const router = useRouter()
const PAGE_SIZE = 24

const loading = ref(true)
const loadingMore = ref(false)
const me = ref(null)
const users = ref([])
const total = ref(0)
const page = ref(0)
const error = ref('')

const filters = reactive({
  keyword: '',
  status: '',
  role: ''
})

const defaultAvatar = 'https://via.placeholder.com/80x80.png?text=OB'

const canAccess = computed(() => {
  const r = me.value?.role
  return r === 'ADMIN' || r === 'AUTHOR'
})

const isAdmin = computed(() => me.value?.role === 'ADMIN')

const hasMore = computed(() => users.value.length < total.value)

function formatTime(iso) {
  if (!iso) return ''
  try { return new Date(iso).toLocaleString('zh-CN') } catch { return '' }
}

function roleLabel(role) {
  if (role === 'ADMIN') return '管理员'
  if (role === 'AUTHOR') return '作者'
  if (role === 'READER') return '读者'
  return role || '—'
}

function statusLabel(status) {
  if (status === 'ACTIVE') return '活跃'
  if (status === 'PENDING') return '待审核'
  if (status === 'BANNED') return '已封禁'
  if (status === 'RESTRICTED') return '限制互动'
  return status || '—'
}

async function loadPage(nextPage, append) {
  error.value = ''
  if (!canAccess.value) { users.value = []; total.value = 0; return }
  const resp = await fetchAdminUsers({
    keyword: filters.keyword,
    status: filters.status,
    role: filters.role,
    page: nextPage,
    size: PAGE_SIZE
  })
  const items = resp?.items || []
  total.value = Number(resp?.total) || 0
  page.value = nextPage
  users.value = append ? [...users.value, ...items] : items
}

async function loadMore() {
  if (!hasMore.value || loadingMore.value) return
  loadingMore.value = true
  try { await loadPage(page.value + 1, true) } catch (e) { error.value = e?.message || '加载失败' } finally { loadingMore.value = false }
}

function applySearch() {
  loading.value = true
  users.value = []
  total.value = 0
  loadPage(0, false).finally(() => { loading.value = false })
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  filters.role = ''
  applySearch()
}

async function handleBan(u) {
  if (!confirm(`确定要封禁用户「${u.displayName || u.username}」吗？封禁后该用户将无法登录。`)) return
  try {
    await changeUserStatus(u.userId, 'BANNED')
    u.status = 'BANNED'
  } catch (e) { alert(e?.message || '操作失败') }
}

async function handleRestrict(u) {
  if (!confirm(`确定要限制用户「${u.displayName || u.username}」的互动权限吗？`)) return
  try {
    await changeUserStatus(u.userId, 'RESTRICTED')
    u.status = 'RESTRICTED'
  } catch (e) { alert(e?.message || '操作失败') }
}

async function handleUnban(u) {
  if (!confirm(`确定要解除对「${u.displayName || u.username}」的限制吗？`)) return
  try {
    await changeUserStatus(u.userId, 'ACTIVE')
    u.status = 'ACTIVE'
  } catch (e) { alert(e?.message || '操作失败') }
}

async function handleRoleChange(u, newRole) {
  if (u.role === newRole) return
  if (!confirm(`确定要将「${u.displayName || u.username}」的角色变更为「${roleLabel(newRole)}」吗？`)) return
  try {
    await changeUserRole(u.userId, newRole)
    u.role = newRole
  } catch (e) { alert(e?.message || '操作失败') }
}

onMounted(async () => {
  loading.value = true
  try {
    try { me.value = await fetchMe() } catch { me.value = null }
    await loadPage(0, false)
  } catch (e) { users.value = []; error.value = e?.message || '加载用户列表失败' } finally { loading.value = false }
})
</script>
```

- [ ] **Step 2: Commit**

```bash
git add vue/src/views/ConsoleUsersView.vue
git commit -m "feat: add user search/filter, action buttons for ban/restrict/role change"
```

---

### Task 9: Frontend — Create ConsoleUserDetailView

**Files:**
- Create: `vue/src/views/ConsoleUserDetailView.vue`

- [ ] **Step 1: Create ConsoleUserDetailView.vue**

```vue
<template>
  <div class="console-page">
    <header class="console-page-header">
      <nav class="console-breadcrumb">
        <router-link to="/console/users">用户管理</router-link>
        <span class="console-breadcrumb-sep">›</span>
        <span>{{ detail?.username || '用户详情' }}</span>
      </nav>
    </header>

    <div v-if="loading" style="color: var(--console-muted, var(--muted))">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <template v-else-if="detail">
      <div class="user-detail-grid">
        <!-- 左栏：信息卡 -->
        <div class="console-card">
          <div class="user-detail-profile">
            <img
              class="user-detail-avatar"
              :src="detail.avatarUrl || defaultAvatar"
              :alt="detail.username || ''"
            />
            <div class="user-detail-names">
              <h2 class="user-detail-username">{{ detail.username }}</h2>
              <div class="user-detail-tags">
                <span class="admin-tag" :class="'tag-role-' + (detail.role || '').toLowerCase()">
                  {{ roleLabel(detail.role) }}
                </span>
                <span class="admin-tag" :class="'tag-status-' + (detail.status || '').toLowerCase()">
                  {{ statusLabel(detail.status) }}
                </span>
              </div>
            </div>
          </div>
          <dl class="user-detail-fields">
            <dt>邮箱</dt>
            <dd>{{ detail.email || '—' }}</dd>
            <dt>简介</dt>
            <dd>{{ detail.bio || '暂无简介' }}</dd>
          </dl>
        </div>

        <!-- 右栏：统计 -->
        <div class="console-card">
          <h3 class="console-card-title">数据统计</h3>
          <div class="user-detail-stats">
            <div class="stat-item">
              <span class="stat-value">{{ detail.articleCount }}</span>
              <span class="stat-label">文章数</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ detail.commentCount }}</span>
              <span class="stat-label">评论数</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ fmtDate(detail.createdAt) }}</span>
              <span class="stat-label">注册时间</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ fmtDate(detail.updatedAt) }}</span>
              <span class="stat-label">最近更新</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 操作面板（仅 ADMIN） -->
      <div v-if="isAdmin" class="console-card" style="margin-top: 16px">
        <h3 class="console-card-title">管理操作</h3>
        <div class="user-detail-ops">
          <div class="op-group">
            <span class="op-label">角色：</span>
            <select
              class="form-input"
              style="width: 140px"
              :value="detail.role"
              @change="handleRoleChange(($event.target).value)"
            >
              <option value="ADMIN">管理员</option>
              <option value="AUTHOR">作者</option>
              <option value="READER">读者</option>
            </select>
          </div>
          <div class="op-group">
            <span class="op-label">状态：</span>
            <template v-if="detail.status === 'ACTIVE'">
              <button type="button" class="btn btn-outline-danger" @click="handleBan">封禁账号</button>
              <button type="button" class="btn btn-outline-warning" @click="handleRestrict">限制互动</button>
            </template>
            <template v-else-if="detail.status === 'BANNED'">
              <button type="button" class="btn btn-outline-success" @click="handleUnban">解除封禁</button>
            </template>
            <template v-else-if="detail.status === 'RESTRICTED'">
              <button type="button" class="btn btn-outline-success" @click="handleUnban">解除限制</button>
            </template>
            <template v-else-if="detail.status === 'PENDING'">
              <span class="op-hint">待审核用户需在审核页面操作</span>
            </template>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchMe } from '../api/admin'
import { fetchUserDetail, changeUserStatus, changeUserRole } from '../api/userAdmin'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const me = ref(null)
const detail = ref(null)
const error = ref('')
const defaultAvatar = 'https://via.placeholder.com/80x80.png?text=OB'

const isAdmin = computed(() => me.value?.role === 'ADMIN')

function roleLabel(role) {
  if (role === 'ADMIN') return '管理员'
  if (role === 'AUTHOR') return '作者'
  if (role === 'READER') return '读者'
  return role || '—'
}

function statusLabel(status) {
  if (status === 'ACTIVE') return '活跃'
  if (status === 'PENDING') return '待审核'
  if (status === 'BANNED') return '已封禁'
  if (status === 'RESTRICTED') return '限制互动'
  return status || '—'
}

function fmtDate(iso) {
  if (!iso) return '—'
  try { return new Date(iso).toLocaleString('zh-CN') } catch { return '—' }
}

async function loadDetail() {
  const userId = route.params.userId
  if (!userId) {
    error.value = '缺少用户 ID'
    return
  }
  try {
    detail.value = await fetchUserDetail(userId)
  } catch (e) {
    error.value = e?.message || '加载用户详情失败'
  }
}

async function handleBan() {
  if (!confirm('确定要封禁该用户吗？')) return
  try {
    await changeUserStatus(detail.value.userId, 'BANNED')
    detail.value.status = 'BANNED'
  } catch (e) { alert(e?.message || '操作失败') }
}

async function handleRestrict() {
  if (!confirm('确定要限制该用户的互动权限吗？')) return
  try {
    await changeUserStatus(detail.value.userId, 'RESTRICTED')
    detail.value.status = 'RESTRICTED'
  } catch (e) { alert(e?.message || '操作失败') }
}

async function handleUnban() {
  if (!confirm('确定要解除该用户的限制吗？')) return
  try {
    await changeUserStatus(detail.value.userId, 'ACTIVE')
    detail.value.status = 'ACTIVE'
  } catch (e) { alert(e?.message || '操作失败') }
}

async function handleRoleChange(newRole) {
  if (detail.value.role === newRole) return
  if (!confirm(`确定要将其角色变更为「${roleLabel(newRole)}」吗？`)) return
  try {
    await changeUserRole(detail.value.userId, newRole)
    detail.value.role = newRole
  } catch (e) { alert(e?.message || '操作失败') }
}

onMounted(async () => {
  try {
    try { me.value = await fetchMe() } catch { me.value = null }
    await loadDetail()
  } finally {
    loading.value = false
  }
})
</script>
```

- [ ] **Step 2: Commit**

```bash
git add vue/src/views/ConsoleUserDetailView.vue
git commit -m "feat: add user detail page with stats and admin action panel"
```

---

### Task 10: Frontend — Add detail page route

**Files:**
- Modify: `vue/src/router/index.js`

- [ ] **Step 1: Add import and route for ConsoleUserDetailView**

Add the import after the existing ConsoleUsersView import:
```js
import ConsoleUserDetailView from '../views/ConsoleUserDetailView.vue'
```

Add the route inside the `/console` children, right after the `users` route:
```js
      { path: 'users/:userId', name: 'consoleUserDetail', component: ConsoleUserDetailView },
```

The children array should look like:
```js
      { path: 'users', name: 'consoleUsers', component: ConsoleUsersView },
      { path: 'users/:userId', name: 'consoleUserDetail', component: ConsoleUserDetailView },
      { path: 'users/pending', name: 'consolePendingUsers', component: ConsolePendingUsersView },
```

**Important:** `users/:userId` must come BEFORE `users/pending` so that `:userId` doesn't catch "pending".

- [ ] **Step 2: Commit**

```bash
git add vue/src/router/index.js
git commit -m "feat: add console user detail route"
```

---

### Task 11: Frontend — Add CSS for admin user management UI

**Files:**
- Modify: `vue/src/assets/blog.css`

- [ ] **Step 1: Append new CSS rules to blog.css**

Add the following CSS at the end of `vue/src/assets/blog.css`:

```css
/* ── Admin user management ── */

.admin-user-filters {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.admin-user-table-wrap {
  overflow-x: auto;
}

.admin-user-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.admin-user-table th {
  text-align: left;
  padding: 10px 12px;
  border-bottom: 2px solid var(--border, #e5e7eb);
  color: var(--muted, #6b7280);
  font-weight: 600;
  font-size: 13px;
  white-space: nowrap;
}

.admin-user-table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--border, #f3f4f6);
  vertical-align: middle;
}

.admin-user-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text, #111827);
  text-decoration: none;
  font-weight: 600;
}

.admin-user-link:hover {
  color: var(--primary, #2563eb);
}

.admin-user-cell-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.admin-user-cell-muted {
  color: var(--muted, #6b7280);
  font-size: 13px;
}

.admin-user-actions {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
}

/* ── Tags ── */

.admin-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.tag-role-admin {
  background: #fee2e2;
  color: #991b1b;
}
[data-theme='dark'] .tag-role-admin {
  background: #7f1d1d;
  color: #fecaca;
}

.tag-role-author {
  background: #dbeafe;
  color: #1e40af;
}
[data-theme='dark'] .tag-role-author {
  background: #1e3a5f;
  color: #bfdbfe;
}

.tag-role-reader {
  background: #d1fae5;
  color: #065f46;
}
[data-theme='dark'] .tag-role-reader {
  background: #064e3b;
  color: #a7f3d0;
}

.tag-status-active {
  background: #d1fae5;
  color: #065f46;
}
[data-theme='dark'] .tag-status-active {
  background: #064e3b;
  color: #a7f3d0;
}

.tag-status-banned {
  background: #fee2e2;
  color: #991b1b;
}
[data-theme='dark'] .tag-status-banned {
  background: #7f1d1d;
  color: #fecaca;
}

.tag-status-restricted {
  background: #ffedd5;
  color: #9a3412;
}
[data-theme='dark'] .tag-status-restricted {
  background: #7c2d12;
  color: #fed7aa;
}

.tag-status-pending {
  background: #fef9c3;
  color: #854d0e;
}
[data-theme='dark'] .tag-status-pending {
  background: #713f12;
  color: #fef08a;
}

/* ── Button variants ── */

.btn-sm {
  padding: 4px 10px;
  font-size: 12px;
}

.btn-outline-danger {
  border: 1px solid #ef4444;
  color: #ef4444;
  background: transparent;
}
.btn-outline-danger:hover {
  background: #ef4444;
  color: #fff;
}

.btn-outline-warning {
  border: 1px solid #f59e0b;
  color: #f59e0b;
  background: transparent;
}
.btn-outline-warning:hover {
  background: #f59e0b;
  color: #fff;
}

.btn-outline-success {
  border: 1px solid #10b981;
  color: #10b981;
  background: transparent;
}
.btn-outline-success:hover {
  background: #10b981;
  color: #fff;
}

.form-input-sm {
  padding: 4px 8px;
  font-size: 12px;
}

/* ── Breadcrumb ── */

.console-breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--muted, #6b7280);
}

.console-breadcrumb a {
  color: var(--primary, #2563eb);
  text-decoration: none;
}

.console-breadcrumb a:hover {
  text-decoration: underline;
}

.console-breadcrumb-sep {
  color: var(--muted, #9ca3af);
}

/* ── User detail page ── */

.user-detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 768px) {
  .user-detail-grid {
    grid-template-columns: 1fr;
  }
}

.user-detail-profile {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.user-detail-avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.user-detail-names {
  flex: 1;
}

.user-detail-username {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 700;
}

.user-detail-tags {
  display: flex;
  gap: 6px;
}

.user-detail-fields {
  margin: 0;
}

.user-detail-fields dt {
  font-size: 12px;
  color: var(--muted, #6b7280);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-top: 14px;
}

.user-detail-fields dd {
  margin: 4px 0 0;
  font-size: 14px;
}

.console-card-title {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 700;
}

.user-detail-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.stat-item {
  text-align: center;
  padding: 12px 8px;
  background: var(--bg, #f9fafb);
  border-radius: 8px;
}

.stat-value {
  display: block;
  font-size: 18px;
  font-weight: 700;
  color: var(--text, #111827);
}

.stat-label {
  display: block;
  font-size: 12px;
  color: var(--muted, #6b7280);
  margin-top: 4px;
}

.user-detail-ops {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.op-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.op-label {
  font-size: 14px;
  font-weight: 600;
  min-width: 50px;
}

.op-hint {
  font-size: 13px;
  color: var(--muted, #6b7280);
}
```

- [ ] **Step 2: Commit**

```bash
git add vue/src/assets/blog.css
git commit -m "feat: add CSS for admin user table, tags, detail page, and button variants"
```

---

### Task 12: Build verification

**Files:** None (verification only)

- [ ] **Step 1: Build backend**

```bash
mvn -DskipTests compile
```

Expected: BUILD SUCCESS

- [ ] **Step 2: Verify frontend compiles**

```bash
cd vue && npm run build
```

Expected: Build completes without errors

- [ ] **Step 3: Commit any fixes if needed, then final commit**

```bash
git add -A
git commit -m "chore: build verification"
```
