package com.yqz.openblog.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.user.dto.AdminUserListItemResponse;
import com.yqz.openblog.user.dto.PendingUserResponse;
import com.yqz.openblog.user.entity.User;
import com.yqz.openblog.user.entity.UserRole;
import com.yqz.openblog.user.repo.UserMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class UserAdminController {

    private final UserMapper userMapper;

    public UserAdminController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
    public ApiResponse<PageResult<AdminUserListItemResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size
    ) {
        Page<User> mpPage = new Page<>(page + 1L, size);
        IPage<User> p = userMapper.selectPage(
                mpPage,
                Wrappers.lambdaQuery(User.class).orderByDesc(User::getCreatedAt));
        List<AdminUserListItemResponse> items = p.getRecords().stream().map(this::toListItem).toList();
        return ApiResponse.ok(new PageResult<>(items, page, size, p.getTotal()));
    }

    @GetMapping("/admin/users/pending")
    @PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
    public ApiResponse<List<PendingUserResponse>> listPendingReaders() {
        List<User> users = userMapper.selectList(
                Wrappers.lambdaQuery(User.class)
                        .eq(User::getStatus, "PENDING")
                        .eq(User::getRole, UserRole.READER)
                        .orderByDesc(User::getCreatedAt));
        List<PendingUserResponse> list = users.stream().map(this::toPending).toList();
        return ApiResponse.ok(list);
    }

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
