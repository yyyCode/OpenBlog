package com.yqz.openblog.sensitiveword.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yqz.openblog.sensitiveword.entity.SensitiveWord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {

    /** 分批插入；唯一键冲突（IGNORE）直接跳过，重复词不会报错。 */
    @Insert("<script>" +
            "INSERT IGNORE INTO sensitive_word " +
            "(word, category, status, remark, created_by, updated_by, created_at, updated_at) VALUES " +
            "<foreach collection='list' item='w' separator=','>" +
            "(#{w.word}, #{w.category}, #{w.status}, #{w.remark}, #{w.createdBy}, #{w.updatedBy}, #{w.createdAt}, #{w.updatedAt})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("list") List<SensitiveWord> list);

    @Select("SELECT DISTINCT category FROM sensitive_word ORDER BY category")
    List<String> selectCategories();

    @Select("SELECT COUNT(*) FROM sensitive_word WHERE word = #{word}")
    int countByWord(@Param("word") String word);

    /** 排除自身后判断是否重名；大小写/重音比较沿用列的排序规则。 */
    @Select("SELECT COUNT(*) FROM sensitive_word WHERE word = #{word} AND id <> #{id}")
    int countByWordExcludingId(@Param("word") String word, @Param("id") Long id);

    @Delete("DELETE FROM sensitive_word")
    int deleteAll();
}
