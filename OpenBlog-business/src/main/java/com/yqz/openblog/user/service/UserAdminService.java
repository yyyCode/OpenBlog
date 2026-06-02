package com.yqz.openblog.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yqz.openblog.article.entity.Article;
import com.yqz.openblog.article.repo.ArticleMapper;
import com.yqz.openblog.comment.entity.Comment;
import com.yqz.openblog.comment.repo.CommentMapper;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.user.entity.User;
import com.yqz.openblog.user.entity.UserRole;
import com.yqz.openblog.user.dto.UserDetailResponse;
import com.yqz.openblog.user.repo.UserMapper;
import org.springframework.stereotype.Service;

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
    public UserDetailResponse getUserDetail(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BizException(4041, "用户不存在");
        }

        long articleCount = articleMapper.selectCount(
                Wrappers.lambdaQuery(Article.class).eq(Article::getAuthorId, userId));
        long commentCount = commentMapper.selectCount(
                Wrappers.lambdaQuery(Comment.class).eq(Comment::getUserId, userId));

        UserDetailResponse r = new UserDetailResponse();
        r.setUserId(u.getId());
        r.setUsername(u.getUsername());
        r.setEmail(u.getEmail());
        r.setAvatarUrl(u.getAvatarUrl());
        r.setBio(u.getBio());
        r.setRole(u.getRole());
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
