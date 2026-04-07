package com.yqz.openblog.comment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yqz.openblog.article.entity.Article;
import com.yqz.openblog.article.entity.ArticleStatus;
import com.yqz.openblog.comment.dto.CommentCreateRequest;
import com.yqz.openblog.comment.dto.CommentThreadResponse;
import com.yqz.openblog.comment.dto.CommentUserResponse;
import com.yqz.openblog.comment.entity.Comment;
import com.yqz.openblog.comment.entity.CommentStatus;
import com.yqz.openblog.article.repo.ArticleMapper;
import com.yqz.openblog.comment.repo.CommentMapper;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.user.entity.User;
import com.yqz.openblog.user.repo.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CommentService {

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;
    private final UserRepository userRepository;

    public CommentService(CommentMapper commentMapper, ArticleMapper articleMapper, UserRepository userRepository) {
        this.commentMapper = commentMapper;
        this.articleMapper = articleMapper;
        this.userRepository = userRepository;
    }

    public PageResult<CommentThreadResponse> listComments(Long articleId, int page, int size) {
        LambdaQueryWrapper<Article> articleW = Wrappers.lambdaQuery();
        articleW.eq(Article::getId, articleId).eq(Article::getStatus, ArticleStatus.PUBLISHED);
        Article article = articleMapper.selectOne(articleW);
        if (article == null) {
            throw new BizException(4041, "文章不存在");
        }

        Page<Comment> mpPage = new Page<>(page + 1L, size);
        LambdaQueryWrapper<Comment> commentW = Wrappers.lambdaQuery();
        commentW.eq(Comment::getArticleId, article.getId())
                .eq(Comment::getStatus, CommentStatus.APPROVED)
                .isNull(Comment::getParentId)
                .orderByAsc(Comment::getCreatedAt);
        IPage<Comment> p = commentMapper.selectPage(mpPage, commentW);

        List<Comment> top = p.getRecords();
        if (top.isEmpty()) {
            return new PageResult<>(List.of(), page, size, p.getTotal());
        }

        // 分层批量拉取子回复（最多 5 层：顶层 + 4 级回复）
        Set<Long> frontier = new HashSet<>(top.stream().map(Comment::getId).toList());
        List<Comment> descendants = new ArrayList<>();
        for (int depth = 0; depth < 4; depth++) {
            if (frontier.isEmpty()) break;
            List<Comment> layer = commentMapper.selectList(
                    Wrappers.lambdaQuery(Comment.class)
                            .eq(Comment::getArticleId, article.getId())
                            .eq(Comment::getStatus, CommentStatus.APPROVED)
                            .in(Comment::getParentId, frontier)
                            .orderByAsc(Comment::getCreatedAt));
            if (layer.isEmpty()) break;
            descendants.addAll(layer);
            frontier = new HashSet<>(layer.stream().map(Comment::getId).toList());
        }

        List<Comment> all = new ArrayList<>(top.size() + descendants.size());
        all.addAll(top);
        all.addAll(descendants);

        Map<Long, CommentThreadResponse> nodeById = new HashMap<>();
        Set<Long> userIds = new HashSet<>();
        for (Comment c : all) {
            nodeById.put(c.getId(), toNode(c));
            userIds.add(c.getUserId());
        }

        Map<Long, User> userById = new HashMap<>();
        for (User u : userRepository.findAllById(userIds)) {
            userById.put(u.getId(), u);
        }
        for (Comment c : all) {
            CommentThreadResponse node = nodeById.get(c.getId());
            if (node == null) continue;
            User u = userById.get(c.getUserId());
            node.setUser(toUser(u, c.getUserId()));
        }

        // 挂载 replies
        // 两层结构：所有后代评论都折叠挂到“顶层评论”的 replies 下
        Map<Long, Long> parentById = new HashMap<>();
        for (Comment c : all) {
            parentById.put(c.getId(), c.getParentId());
        }
        Set<Long> topIds = new HashSet<>(top.stream().map(Comment::getId).toList());
        for (Comment c : descendants) {
            Long topId = findTopAncestorId(c.getId(), parentById, topIds);
            if (topId == null) continue;
            CommentThreadResponse topNode = nodeById.get(topId);
            CommentThreadResponse child = nodeById.get(c.getId());
            if (topNode == null || child == null) continue;
            // 强制让前端按两层渲染
            child.setReplies(List.of());
            topNode.getReplies().add(child);
        }

        // 每个节点的 replies 按时间排序（稳定）
        Comparator<CommentThreadResponse> byTime = Comparator
                .comparing(CommentThreadResponse::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CommentThreadResponse::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        for (CommentThreadResponse n : nodeById.values()) {
            if (n.getReplies() != null && n.getReplies().size() > 1) {
                n.getReplies().sort(byTime);
            }
        }

        List<CommentThreadResponse> items = top.stream().map(c -> nodeById.get(c.getId())).toList();
        return new PageResult<>(items, page, size, p.getTotal());
    }

    private Long findTopAncestorId(Long id, Map<Long, Long> parentById, Set<Long> topIds) {
        Long cur = id;
        int guard = 0;
        while (cur != null && guard++ < 16) {
            if (topIds.contains(cur)) return cur;
            cur = parentById.get(cur);
        }
        return null;
    }

    private CommentThreadResponse toNode(Comment c) {
        CommentThreadResponse resp = new CommentThreadResponse();
        resp.setId(c.getId());
        resp.setArticleId(c.getArticleId());
        resp.setParentId(c.getParentId());
        resp.setContent(c.getContent());
        resp.setCreatedAt(c.getCreatedAt());
        resp.setUpdatedAt(c.getUpdatedAt());
        return resp;
    }

    private CommentUserResponse toUser(User u, Long uid) {
        CommentUserResponse cu = new CommentUserResponse();
        cu.setId(uid);
        if (u == null) {
            cu.setNickname(null);
            cu.setAvatarUrl(null);
            return cu;
        }
        String nn = u.getNickname();
        if (nn == null || nn.isBlank()) nn = u.getUsername();
        cu.setNickname(nn);
        cu.setAvatarUrl(u.getAvatarUrl());
        return cu;
    }

    public CommentThreadResponse createTopLevel(Long articleId, Long uid, CommentCreateRequest req) {
        LambdaQueryWrapper<Article> articleW = Wrappers.lambdaQuery();
        articleW.eq(Article::getId, articleId).eq(Article::getStatus, ArticleStatus.PUBLISHED);
        Article article = articleMapper.selectOne(articleW);
        if (article == null) {
            throw new BizException(4041, "文章不存在");
        }

        ensureUserActive(uid);

        Comment c = new Comment();
        c.setArticleId(article.getId());
        c.setUserId(uid);
        c.setParentId(null);
        c.setContent(req.getContent());
        c.setStatus(CommentStatus.APPROVED);
        commentMapper.insert(c);

        article.setCommentCount(article.getCommentCount() + 1);
        articleMapper.updateById(article);
        CommentThreadResponse node = toNode(c);
        node.setUser(toUser(userRepository.findById(uid).orElse(null), uid));
        return node;
    }

    public CommentThreadResponse reply(Long commentId, Long uid, CommentCreateRequest req) {
        ensureUserActive(uid);

        Comment parent = commentMapper.selectOne(
                Wrappers.lambdaQuery(Comment.class)
                        .eq(Comment::getId, commentId)
                        .eq(Comment::getStatus, CommentStatus.APPROVED));
        if (parent == null) {
            throw new BizException(4041, "评论不存在");
        }

        LambdaQueryWrapper<Article> articleW = Wrappers.lambdaQuery();
        articleW.eq(Article::getId, parent.getArticleId()).eq(Article::getStatus, ArticleStatus.PUBLISHED);
        Article article = articleMapper.selectOne(articleW);
        if (article == null) {
            throw new BizException(4041, "文章不存在");
        }

        int parentDepth = calcDepth(parent.getId());
        int newDepth = parentDepth + 1;
        if (newDepth > 5) {
            throw new BizException(4002, "回复层级过深（最大 5 级）");
        }

        Comment c = new Comment();
        c.setArticleId(article.getId());
        c.setUserId(uid);
        c.setParentId(parent.getId());
        c.setContent(req.getContent());
        c.setStatus(CommentStatus.APPROVED);
        commentMapper.insert(c);

        article.setCommentCount(article.getCommentCount() + 1);
        articleMapper.updateById(article);

        CommentThreadResponse node = toNode(c);
        node.setUser(toUser(userRepository.findById(uid).orElse(null), uid));
        return node;
    }

    private int calcDepth(Long commentId) {
        int depth = 1;
        Long curId = commentId;
        while (true) {
            Comment cur = commentMapper.selectOne(
                    Wrappers.lambdaQuery(Comment.class)
                            .eq(Comment::getId, curId)
                            .eq(Comment::getStatus, CommentStatus.APPROVED));
            if (cur == null || cur.getParentId() == null) {
                return depth;
            }
            depth++;
            curId = cur.getParentId();
        }
    }

    public void deleteComment(Long commentId, Long uid, boolean isAdmin) {
        Comment c = commentMapper.selectById(commentId);
        if (c == null) {
            throw new BizException(4041, "评论不存在");
        }
        if (c.getStatus() == CommentStatus.DELETED) {
            return;
        }
        if (!isAdmin && !c.getUserId().equals(uid)) {
            throw new BizException(4031, "无权限");
        }
        c.setStatus(CommentStatus.DELETED);
        commentMapper.updateById(c);

        Article article = articleMapper.selectById(c.getArticleId());
        if (article != null && article.getCommentCount() > 0) {
            article.setCommentCount(article.getCommentCount() - 1);
            articleMapper.updateById(article);
        }
    }

    private void ensureUserActive(Long uid) {
        User u = userRepository.findById(uid).orElseThrow(() -> new BizException(4041, "用户不存在"));
        if (!"ACTIVE".equals(u.getStatus())) {
            throw new BizException(4011, "账号已被封禁");
        }
    }
}

