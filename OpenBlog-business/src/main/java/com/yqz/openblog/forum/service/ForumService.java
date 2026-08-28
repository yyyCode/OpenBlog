package com.yqz.openblog.forum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.forum.dto.ForumCommentRequest;
import com.yqz.openblog.forum.dto.ForumCommentResponse;
import com.yqz.openblog.forum.dto.ForumTopicCreateRequest;
import com.yqz.openblog.forum.dto.ForumTopicResponse;
import com.yqz.openblog.forum.entity.ForumComment;
import com.yqz.openblog.forum.entity.ForumCommentStatus;
import com.yqz.openblog.forum.entity.ForumTopic;
import com.yqz.openblog.forum.entity.ForumTopicStatus;
import com.yqz.openblog.forum.repo.ForumCommentMapper;
import com.yqz.openblog.forum.repo.ForumTopicMapper;
import com.yqz.openblog.forum.filter.SensitiveWordFilter;
import com.yqz.openblog.user.entity.User;
import com.yqz.openblog.user.repo.UserRepository;
import org.springframework.stereotype.Service;

import java.util.*;

import java.util.stream.Collectors;

@Service
public class ForumService {

    private final ForumTopicMapper forumTopicMapper;
    private final ForumCommentMapper forumCommentMapper;
    private final UserRepository userRepository;
    private final SensitiveWordFilter sensitiveWordFilter;

    public ForumService(ForumTopicMapper forumTopicMapper, ForumCommentMapper forumCommentMapper,
                        UserRepository userRepository, SensitiveWordFilter sensitiveWordFilter) {
        this.forumTopicMapper = forumTopicMapper;
        this.forumCommentMapper = forumCommentMapper;
        this.userRepository = userRepository;
        this.sensitiveWordFilter = sensitiveWordFilter;
    }

    /**
     * 公开话题列表（仅 PUBLISHED，按创建时间倒序）。
     */
    public PageResult<ForumTopicResponse> listTopics(int page, int size) {
        LambdaQueryWrapper<ForumTopic> w = Wrappers.lambdaQuery(ForumTopic.class)
                .eq(ForumTopic::getStatus, ForumTopicStatus.PUBLISHED)
                .orderByDesc(ForumTopic::getCreatedAt);

        IPage<ForumTopic> p = forumTopicMapper.selectPage(new Page<>(page + 1, size), w);
        return buildTopicPageResult(p, page, size);
    }

    /**
     * 管理后台话题列表（可按状态筛选）。
     */
    public PageResult<ForumTopicResponse> listAllTopics(int page, int size, String statusFilter) {
        LambdaQueryWrapper<ForumTopic> w = Wrappers.lambdaQuery(ForumTopic.class)
                .orderByDesc(ForumTopic::getCreatedAt);
        if (statusFilter != null && !statusFilter.isBlank()) {
            w.eq(ForumTopic::getStatus, ForumTopicStatus.valueOf(statusFilter));
        }

        IPage<ForumTopic> p = forumTopicMapper.selectPage(new Page<>(page + 1, size), w);
        return buildTopicPageResult(p, page, size);
    }

    /**
     * 话题详情（含 +1 浏览计数）。
     */
    public ForumTopicResponse getTopic(Long topicId) {
        ForumTopic topic = forumTopicMapper.selectById(topicId);
        if (topic == null || topic.getStatus() == ForumTopicStatus.HIDDEN) {
            throw new BizException(4041, "话题不存在");
        }

        // +1 浏览
        topic.setViewCount(topic.getViewCount() + 1);
        forumTopicMapper.updateById(topic);

        return toTopicResponse(topic);
    }

    /**
     * 创建话题。
     */
    public ForumTopicResponse createTopic(Long uid, ForumTopicCreateRequest req) {
        ensureUserActive(uid);
        checkSensitive(req.getTitle(), req.getContent());

        ForumTopic topic = new ForumTopic();
        topic.setTitle(req.getTitle());
        topic.setContent(req.getContent());
        topic.setAuthorId(uid);
        topic.setStatus(ForumTopicStatus.PUBLISHED);
        forumTopicMapper.insert(topic);

        return toTopicResponse(topic);
    }

    /**
     * 管理后台：隐藏话题。
     */
    public void hideTopic(Long topicId) {
        ForumTopic topic = forumTopicMapper.selectById(topicId);
        if (topic == null) {
            throw new BizException(4041, "话题不存在");
        }
        topic.setStatus(ForumTopicStatus.HIDDEN);
        forumTopicMapper.updateById(topic);
    }

    /**
     * 管理后台：恢复话题。
     */
    public void publishTopic(Long topicId) {
        ForumTopic topic = forumTopicMapper.selectById(topicId);
        if (topic == null) {
            throw new BizException(4041, "话题不存在");
        }
        topic.setStatus(ForumTopicStatus.PUBLISHED);
        forumTopicMapper.updateById(topic);
    }

    /**
     * 话题评论列表（按时间正序）。
     */
    public PageResult<ForumCommentResponse> listComments(Long topicId, int page, int size) {
        ForumTopic topic = forumTopicMapper.selectById(topicId);
        if (topic == null || topic.getStatus() == ForumTopicStatus.HIDDEN) {
            throw new BizException(4041, "话题不存在");
        }

        LambdaQueryWrapper<ForumComment> w = Wrappers.lambdaQuery(ForumComment.class)
                .eq(ForumComment::getTopicId, topicId)
                .eq(ForumComment::getStatus, ForumCommentStatus.APPROVED)
                .orderByAsc(ForumComment::getCreatedAt);

        IPage<ForumComment> p = forumCommentMapper.selectPage(new Page<>(page + 1, size), w);

        Set<Long> userIds = p.getRecords().stream().map(ForumComment::getAuthorId).collect(Collectors.toSet());
        Map<Long, User> userMap = loadUserMap(userIds);

        List<ForumCommentResponse> items = p.getRecords().stream().map(c -> toCommentResponse(c, userMap)).collect(Collectors.toList());
        return new PageResult<>(items, page, size, p.getTotal());
    }

    /**
     * 发表评论。
     */
    public ForumCommentResponse createComment(Long topicId, Long uid, ForumCommentRequest req) {
        ensureUserActive(uid);
        String content = req.getContent();
        checkSensitive(content);
        if (content.split("\n", -1).length > 10) {
            throw new BizException(4001, "评论不能超过 10 行");
        }

        ForumTopic topic = forumTopicMapper.selectById(topicId);
        if (topic == null || topic.getStatus() == ForumTopicStatus.HIDDEN) {
            throw new BizException(4041, "话题不存在");
        }

        ForumComment c = new ForumComment();
        c.setTopicId(topicId);
        c.setAuthorId(uid);
        c.setContent(req.getContent());
        c.setStatus(ForumCommentStatus.APPROVED);
        forumCommentMapper.insert(c);

        // 更新话题评论计数
        topic.setCommentCount(topic.getCommentCount() + 1);
        forumTopicMapper.updateById(topic);

        User u = userRepository.findById(uid).orElse(null);
        ForumCommentResponse resp = new ForumCommentResponse();
        resp.setId(c.getId());
        resp.setTopicId(c.getTopicId());
        resp.setContent(c.getContent());
        resp.setAuthorId(uid);
        resp.setAuthorName(u != null ? u.getUsername() : null);
        resp.setAuthorAvatar(u != null ? u.getAvatarUrl() : null);
        resp.setCreatedAt(c.getCreatedAt());
        return resp;
    }

    /**
     * 删除评论（软删除：本人或管理员可删）。
     */
    public void deleteComment(Long commentId, Long uid, boolean isAdmin) {
        ForumComment c = forumCommentMapper.selectById(commentId);
        if (c == null) {
            throw new BizException(4041, "评论不存在");
        }
        if (c.getStatus() == ForumCommentStatus.DELETED) {
            return;
        }
        if (!isAdmin && !c.getAuthorId().equals(uid)) {
            throw new BizException(4031, "无权限");
        }
        c.setStatus(ForumCommentStatus.DELETED);
        forumCommentMapper.updateById(c);

        // 减少话题评论计数
        ForumTopic topic = forumTopicMapper.selectById(c.getTopicId());
        if (topic != null && topic.getCommentCount() > 0) {
            topic.setCommentCount(topic.getCommentCount() - 1);
            forumTopicMapper.updateById(topic);
        }
    }

    // ---- 内部方法 ----

    private PageResult<ForumTopicResponse> buildTopicPageResult(IPage<ForumTopic> p, int page, int size) {
        Set<Long> authorIds = p.getRecords().stream().map(ForumTopic::getAuthorId).collect(Collectors.toSet());
        Map<Long, User> userMap = loadUserMap(authorIds);

        List<ForumTopicResponse> items = p.getRecords().stream()
                .map(t -> toTopicResponse(t, userMap))
                .collect(Collectors.toList());
        return new PageResult<>(items, page, size, p.getTotal());
    }

    private ForumTopicResponse toTopicResponse(ForumTopic t) {
        return toTopicResponse(t, Collections.emptyMap());
    }

    private ForumTopicResponse toTopicResponse(ForumTopic t, Map<Long, User> userMap) {
        ForumTopicResponse r = new ForumTopicResponse();
        r.setId(t.getId());
        r.setTitle(t.getTitle());
        r.setContent(t.getContent());
        r.setStatus(t.getStatus().name());
        r.setAuthorId(t.getAuthorId());
        r.setViewCount(t.getViewCount());
        r.setCommentCount(t.getCommentCount());
        r.setCreatedAt(t.getCreatedAt());
        r.setUpdatedAt(t.getUpdatedAt());

        User author = userMap.get(t.getAuthorId());
        r.setAuthorName(author != null ? author.getUsername() : null);
        r.setAuthorAvatar(author != null ? author.getAvatarUrl() : null);
        return r;
    }

    private ForumCommentResponse toCommentResponse(ForumComment c, Map<Long, User> userMap) {
        ForumCommentResponse r = new ForumCommentResponse();
        r.setId(c.getId());
        r.setTopicId(c.getTopicId());
        r.setContent(c.getContent());
        r.setAuthorId(c.getAuthorId());
        r.setCreatedAt(c.getCreatedAt());

        User u = userMap.get(c.getAuthorId());
        r.setAuthorName(u != null ? u.getUsername() : null);
        r.setAuthorAvatar(u != null ? u.getAvatarUrl() : null);
        return r;
    }

    private Map<Long, User> loadUserMap(Set<Long> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }

    private void ensureUserActive(Long uid) {
        User u = userRepository.findById(uid).orElseThrow(() -> new BizException(4041, "用户不存在"));
        if ("BANNED".equals(u.getStatus())) {
            throw new BizException(4011, "账号已被封禁");
        }
    }

    private void checkSensitive(String... texts) {
        for (String text : texts) {
            if (text == null || text.isBlank()) continue;
            if (sensitiveWordFilter.contains(text)) {
                List<String> words = sensitiveWordFilter.findAll(text);
                throw new BizException(4003, "内容包含敏感词，请修改后重试");
            }
        }
    }
}
