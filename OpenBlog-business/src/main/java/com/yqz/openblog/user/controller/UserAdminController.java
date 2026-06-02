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
