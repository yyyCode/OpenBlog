package com.yqz.openblog.sensitiveword.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yqz.openblog.sensitiveword.entity.SensitiveWordChangeLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SensitiveWordChangeLogMapper extends BaseMapper<SensitiveWordChangeLog> {

    /** 发布后把尚未归属版本的变更日志挂到本次版本。 */
    @Update("UPDATE sensitive_word_change_log SET version_id = #{versionId} WHERE version_id IS NULL")
    int assignVersionToUnpublished(@Param("versionId") Long versionId);

    /** 批量写导入/回滚变更日志，避免逐词单条插入。 */
    @Insert("<script>" +
            "INSERT INTO sensitive_word_change_log " +
            "(word, action, before_json, after_json, version_id, operator, operated_at) VALUES " +
            "<foreach collection='list' item='c' separator=','>" +
            "(#{c.word}, #{c.action}, #{c.beforeJson}, #{c.afterJson}, #{c.versionId}, #{c.operator}, #{c.operatedAt})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("list") List<SensitiveWordChangeLog> list);
}
