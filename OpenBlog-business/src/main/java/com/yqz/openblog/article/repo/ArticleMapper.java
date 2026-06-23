package com.yqz.openblog.article.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yqz.openblog.article.entity.Article;
import com.yqz.openblog.article.entity.ArticleStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    @Update("update articles set view_count = view_count + 1 where id = #{articleId} and status = #{status}")
    int incrementViewCount(@Param("articleId") Long articleId, @Param("status") ArticleStatus status);

    @Select("""
            select id
            from articles
            where status = 'SCHEDULED'
              and scheduled_at is not null
              and scheduled_at <= #{now}
            order by scheduled_at asc
            limit #{limit}
            """)
    List<Long> listDueScheduledIds(@Param("now") Instant now, @Param("limit") int limit);

    /**
     * 原子发布：仅当仍为 SCHEDULED 且已到点时才会成功（多实例下确保只发布一次）。
     */
    @Update("""
            update articles
            set status = 'PUBLISHED',
                published_at = scheduled_at,
                scheduled_at = null,
                updated_at = now(6)
            where id = #{articleId}
              and status = 'SCHEDULED'
              and scheduled_at is not null
              and scheduled_at <= #{now}
            """)
    int publishScheduledIfDue(@Param("articleId") Long articleId, @Param("now") Instant now);

    /**
     * FULLTEXT 搜索已发布文章的正文，返回匹配的文章 ID 列表（按相关性降序）。
     */
    @Select("""
            select a.id
            from articles a
            join article_bodies b on a.id = b.article_id
            where a.status = 'PUBLISHED'
              and match(b.content_markdown) against(#{keyword} in natural language mode)
            order by a.published_at desc
            limit 50
            """)
    List<Long> searchIdsByFulltext(@Param("keyword") String keyword);
}

