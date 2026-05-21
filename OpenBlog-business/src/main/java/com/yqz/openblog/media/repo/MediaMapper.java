package com.yqz.openblog.media.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yqz.openblog.media.entity.Media;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MediaMapper extends BaseMapper<Media> {
}

