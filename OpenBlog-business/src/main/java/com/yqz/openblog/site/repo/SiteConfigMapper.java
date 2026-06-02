package com.yqz.openblog.site.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yqz.openblog.site.entity.SiteConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * site_config 表 Mapper
 */
@Mapper
public interface SiteConfigMapper extends BaseMapper<SiteConfig> {

    @Update("INSERT INTO site_config (config_key, config_value) VALUES (#{key}, #{value}) "
          + "ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), updated_at = NOW()")
    int upsert(@Param("key") String key, @Param("value") String value);
}
