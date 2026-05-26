package com.yqz.openblog.article.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yqz.openblog.article.entity.ArticleBody;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ArticleBodyMapper extends BaseMapper<ArticleBody> {

    @Update("""
            update article_bodies
            set content_html = #{html},
                word_count = #{wordCount},
                updated_at = now(6)
            where article_id = #{articleId}
              and content_html is null
            """)
    int backfillHtmlIfNull(@Param("articleId") Long articleId,
                           @Param("html") String html,
                           @Param("wordCount") int wordCount);
}
