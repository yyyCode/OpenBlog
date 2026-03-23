package com.yqz.openblog.article.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yqz.openblog.article.entity.Article;
import com.yqz.openblog.article.entity.ArticleStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    @Update("update articles set view_count = view_count + 1 where id = #{articleId} and status = #{status}")
    int incrementViewCount(@Param("articleId") Long articleId, @Param("status") ArticleStatus status);
}

