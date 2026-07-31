package com.yqz.openblog.email.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yqz.openblog.email.entity.EmailRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmailRecordMapper extends BaseMapper<EmailRecord> {
}
