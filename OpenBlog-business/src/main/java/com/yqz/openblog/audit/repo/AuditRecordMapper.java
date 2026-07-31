package com.yqz.openblog.audit.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yqz.openblog.audit.entity.AuditRecordEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditRecordMapper extends BaseMapper<AuditRecordEntity> {
}
