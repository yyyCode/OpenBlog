package com.yqz.openblog.user.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.user.dto.PublicProfileResponse;
import com.yqz.openblog.user.entity.User;
import com.yqz.openblog.user.entity.UserRole;
import com.yqz.openblog.media.service.MediaService;
import com.yqz.openblog.user.repo.UserMapper;
import com.yqz.openblog.security.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class PublicProfileController {

    private final UserMapper userMapper;
    private final CurrentUser currentUser;
    private final MediaService mediaService;

    public PublicProfileController(UserMapper userMapper, CurrentUser currentUser, MediaService mediaService) {
        this.userMapper = userMapper;
        this.currentUser = currentUser;
        this.mediaService = mediaService;
    }

    @GetMapping("/profile")
    public ApiResponse<PublicProfileResponse> profile() {
        // 如果前端携带了 JWT，则优先返回“当前登录用户”的公开信息
        // 这样在管理员/作者编辑后，前台页面也能立刻反映你改的用户名/签名/头像。
        Long uid = currentUser.userId();
        if (uid != null) {
            User u = userMapper.selectById(uid);
            if (u != null) {
                PublicProfileResponse resp = new PublicProfileResponse();
                resp.setUserId(u.getId());
                resp.setUsername(u.getUsername());
                resp.setAvatarUrl(mediaService.normalizePublicMediaUrl(u.getAvatarUrl()));
                resp.setBio(u.getBio());
                return ApiResponse.ok(resp);
            }
        }

        // MVP：个人博客通常只有一个作者，但数据库可能存在多条 AUTHOR 数据：
        // 优先展示最近一次更新的 ACTIVE 作者，避免前端一直显示旧数据。
        User u = userMapper.selectOne(
                Wrappers.lambdaQuery(User.class)
                        .eq(User::getRole, UserRole.AUTHOR)
                        .eq(User::getStatus, "ACTIVE")
                        .orderByDesc(User::getUpdatedAt)
        );
        if (u == null) {
            u = userMapper.selectOne(
                    Wrappers.lambdaQuery(User.class)
                            .eq(User::getRole, UserRole.AUTHOR)
                            .orderByAsc(User::getId)
            );
        }
        if (u == null) {
            u = userMapper.selectOne(Wrappers.lambdaQuery(User.class).orderByAsc(User::getId));
        }
        if (u == null) {
            return ApiResponse.ok(new PublicProfileResponse());
        }

        PublicProfileResponse resp = new PublicProfileResponse();
        resp.setUserId(u.getId());
        resp.setUsername(u.getUsername());
        resp.setAvatarUrl(mediaService.normalizePublicMediaUrl(u.getAvatarUrl()));
        resp.setBio(u.getBio());
        return ApiResponse.ok(resp);
    }
}

