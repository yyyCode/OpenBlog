package com.yqz.openblog.forum.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yqz.openblog.forum.entity.ForumTopic;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ForumTopicMapper extends BaseMapper<ForumTopic> {
}
