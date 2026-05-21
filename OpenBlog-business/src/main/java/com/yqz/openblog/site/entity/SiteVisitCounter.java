package com.yqz.openblog.site.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 全站访问累计（单行 id=1）。
 */
@TableName("site_visit_counter")
public class SiteVisitCounter {

    public static final int SINGLETON_ID = 1;

    @TableId(value = "id", type = IdType.INPUT)
    private Integer id;

    private Long visitCount;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Long getVisitCount() {
        return visitCount;
    }

    public void setVisitCount(Long visitCount) {
        this.visitCount = visitCount;
    }
}
