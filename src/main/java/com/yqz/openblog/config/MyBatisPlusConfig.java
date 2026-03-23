package com.yqz.openblog.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.apache.ibatis.annotations.Mapper;

@Configuration
// 只扫描显式标注了 @Mapper 的接口，避免把旧的 Spring Data JPA Repository
// （例如 *Repository extends JpaRepository）也当成 MyBatis Mapper 注册导致 bean 冲突。
@MapperScan(basePackages = "com.yqz.openblog", annotationClass = Mapper.class)
public class MyBatisPlusConfig {
}

