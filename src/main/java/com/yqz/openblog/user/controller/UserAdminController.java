package com.yqz.openblog.user.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.BizException;
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
}
