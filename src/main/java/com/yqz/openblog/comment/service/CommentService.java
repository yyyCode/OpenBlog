package com.yqz.openblog.comment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yqz.openblog.article.entity.Article;
import com.yqz.openblog.article.entity.ArticleStatus;
import com.yqz.openblog.comment.dto.CommentCreateRequest;
import com.yqz.openblog.comment.dto.CommentResponse;
import com.yqz.openblog.comment.entity.Comment;
import com.yqz.openblog.comment.entity.CommentStatus;
import com.yqz.openblog.article.repo.ArticleMapper;
import com.yqz.openblog.comment.repo.CommentMapper;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.security.CurrentUser;
import com.yqz.openblog.user.entity.User;
import com.yqz.openblog.user.repo.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    public PageResult<CommentResponse> listComments(Long articleId, int page, int size) {
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
                .orderByAsc(Comment::getCreatedAt);
        IPage<Comment> p = commentMapper.selectPage(mpPage, commentW);

        List<CommentResponse> items = p.getRecords().stream().map(this::mapComment).toList();
        return new PageResult<>(items, page, size, p.getTotal());
    }

    private CommentResponse mapComment(Comment c) {
        CommentResponse resp = new CommentResponse();
        resp.setId(c.getId());
        resp.setArticleId(c.getArticleId());
        resp.setUserId(c.getUserId());
        resp.setUserNickname(userRepository.findById(c.getUserId()).map(User::getNickname).orElse(null));
        resp.setParentId(c.getParentId());
        resp.setContent(c.getContent());
        resp.setCreatedAt(c.getCreatedAt());
        resp.setUpdatedAt(c.getUpdatedAt());
        return resp;
    }

    public CommentResponse createTopLevel(Long articleId, Long uid, CommentCreateRequest req) {
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
        return mapComment(c);
    }

    public CommentResponse reply(Long commentId, Long uid, CommentCreateRequest req) {
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

        return mapComment(c);
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

