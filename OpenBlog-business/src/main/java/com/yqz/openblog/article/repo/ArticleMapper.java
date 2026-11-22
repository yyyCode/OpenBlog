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
     * ES 不可用时的降级搜索：标题/摘要 LIKE + 正文 FULLTEXT，按发布时间倒序分页。
     *
     * <p>标题与摘要用 LIKE 而非 FULLTEXT，因此 LIKE 部分需要由调用方转义
     * {@code % _ \\} 并传 {@code likePattern}；正文 MATCH 用原始 {@code keyword}。</p>
     */
    @Select("""
            <script>
            select a.id
            from articles a
            left join article_bodies b on a.id = b.article_id
            where a.status = 'PUBLISHED'
              and (a.title like #{likePattern}
                   or a.summary like #{likePattern}
                   or match(b.content_markdown) against(#{keyword} in natural language mode))
            <if test="categoryId != null">and a.category_id = #{categoryId}</if>
            <if test="type != null">and a.type = #{type}</if>
            order by a.published_at desc, a.id desc
            limit #{offset}, #{size}
            </script>
            """)
    List<Long> searchPublishedIds(@Param("keyword") String keyword,
                                  @Param("likePattern") String likePattern,
                                  @Param("categoryId") Long categoryId,
                                  @Param("type") String type,
                                  @Param("offset") long offset,
                                  @Param("size") int size);

    /** 降级搜索的总命中数，条件必须与 {@link #searchPublishedIds} 保持一致。 */
    @Select("""
            <script>
            select count(*)
            from articles a
            left join article_bodies b on a.id = b.article_id
            where a.status = 'PUBLISHED'
              and (a.title like #{likePattern}
                   or a.summary like #{likePattern}
                   or match(b.content_markdown) against(#{keyword} in natural language mode))
            <if test="categoryId != null">and a.category_id = #{categoryId}</if>
            <if test="type != null">and a.type = #{type}</if>
            </script>
            """)
    long countPublishedSearch(@Param("keyword") String keyword,
                              @Param("likePattern") String likePattern,
                              @Param("categoryId") Long categoryId,
                              @Param("type") String type);
}

