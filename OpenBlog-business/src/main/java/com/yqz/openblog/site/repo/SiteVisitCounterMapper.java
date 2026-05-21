package com.yqz.openblog.site.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yqz.openblog.site.entity.SiteVisitCounter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SiteVisitCounterMapper extends BaseMapper<SiteVisitCounter> {

    @Update("UPDATE site_visit_counter SET visit_count = visit_count + 1 WHERE id = 1")
    int incrementVisitCount();
}
