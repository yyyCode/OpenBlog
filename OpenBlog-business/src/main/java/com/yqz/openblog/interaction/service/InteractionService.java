package com.yqz.openblog.interaction.service;

import com.yqz.openblog.article.entity.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yqz.openblog.article.repo.ArticleFavoriteMapper;
import com.yqz.openblog.article.repo.ArticleLikeMapper;
import com.yqz.openblog.article.repo.ArticleMapper;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.user.entity.User;
import com.yqz.openblog.user.entity.UserFollow;
import com.yqz.openblog.user.repo.UserFollowMapper;
import com.yqz.openblog.user.repo.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class InteractionService {

    private final ArticleMapper articleMapper;
    private final ArticleLikeMapper likeMapper;
    private final ArticleFavoriteMapper favoriteMapper;
    private final UserFollowMapper followMapper;
    private final UserMapper userMapper;

    public InteractionService(ArticleMapper articleMapper,
                               ArticleLikeMapper likeMapper,
                               ArticleFavoriteMapper favoriteMapper,
                               UserFollowMapper followMapper,
                               UserMapper userMapper) {
        this.articleMapper = articleMapper;
        this.likeMapper = likeMapper;
        this.favoriteMapper = favoriteMapper;
        this.followMapper = followMapper;
        this.userMapper = userMapper;
    }

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

    public void like(Long articleId, Long uid) {
        ensureUserActive(uid);
        Article a = articleMapper.selectOne(Wrappers.lambdaQuery(Article.class)
                .eq(Article::getId, articleId)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED));
        if (a == null) {
            throw new BizException(4041, "文章不存在");
        }

        ArticleLike existing = likeMapper.selectOne(Wrappers.lambdaQuery(ArticleLike.class)
                .eq(ArticleLike::getArticleId, articleId)
                .eq(ArticleLike::getUserId, uid));
        if (existing != null) {
            return;
        }
        likeMapper.insert(new ArticleLike(articleId, uid));
        a.setLikeCount(a.getLikeCount() + 1);
        articleMapper.updateById(a);
    }

    public void unlike(Long articleId, Long uid) {
        ensureUserActive(uid);
        Article a = articleMapper.selectOne(Wrappers.lambdaQuery(Article.class)
                .eq(Article::getId, articleId)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED));
        if (a == null) {
            throw new BizException(4041, "文章不存在");
        }

        likeMapper.delete(Wrappers.lambdaQuery(ArticleLike.class)
                .eq(ArticleLike::getArticleId, articleId)
                .eq(ArticleLike::getUserId, uid));
        if (a.getLikeCount() != null && a.getLikeCount() > 0) {
            a.setLikeCount(a.getLikeCount() - 1);
            articleMapper.updateById(a);
        }
    }

    public void favorite(Long articleId, Long uid) {
        ensureUserActive(uid);
        Article a = articleMapper.selectOne(Wrappers.lambdaQuery(Article.class)
                .eq(Article::getId, articleId)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED));
        if (a == null) {
            throw new BizException(4041, "文章不存在");
        }

        ArticleFavorite existing = favoriteMapper.selectOne(Wrappers.lambdaQuery(ArticleFavorite.class)
                .eq(ArticleFavorite::getArticleId, articleId)
                .eq(ArticleFavorite::getUserId, uid));
        if (existing != null) {
            return;
        }
        favoriteMapper.insert(new ArticleFavorite(articleId, uid));
        a.setFavoriteCount(a.getFavoriteCount() + 1);
        articleMapper.updateById(a);
    }

    public void unfavorite(Long articleId, Long uid) {
        ensureUserActive(uid);
        Article a = articleMapper.selectOne(Wrappers.lambdaQuery(Article.class)
                .eq(Article::getId, articleId)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED));
        if (a == null) {
            throw new BizException(4041, "文章不存在");
        }

        favoriteMapper.delete(Wrappers.lambdaQuery(ArticleFavorite.class)
                .eq(ArticleFavorite::getArticleId, articleId)
                .eq(ArticleFavorite::getUserId, uid));
        if (a.getFavoriteCount() != null && a.getFavoriteCount() > 0) {
            a.setFavoriteCount(a.getFavoriteCount() - 1);
            articleMapper.updateById(a);
        }
    }

    public void follow(Long followeeId, Long followerId) {
        ensureUserActive(followerId);
        if (followeeId.equals(followerId)) {
            throw new BizException(4002, "不能关注自己");
        }
        User followee = userMapper.selectById(followeeId);
        if (followee == null) {
            throw new BizException(4041, "用户不存在");
        }
        if (!"ACTIVE".equals(followee.getStatus())) {
            throw new BizException(4041, "用户不存在");
        }

        UserFollow existing = followMapper.selectOne(Wrappers.lambdaQuery(UserFollow.class)
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId));
        if (existing != null) {
            return;
        }
        followMapper.insert(new UserFollow(followerId, followeeId));
    }

    public void unfollow(Long followeeId, Long followerId) {
        ensureUserActive(followerId);
        followMapper.delete(Wrappers.lambdaQuery(UserFollow.class)
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId));
    }
}

