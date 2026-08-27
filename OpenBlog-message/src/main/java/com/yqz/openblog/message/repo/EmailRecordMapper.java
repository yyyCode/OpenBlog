package com.yqz.openblog.message.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yqz.openblog.message.entity.EmailRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmailRecordMapper extends BaseMapper<EmailRecord> {
}
