package com.yqz.openblog.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
// 只扫描显式标注了 @Mapper 的接口，避免把旧的 Spring Data JPA Repository
// （例如 *Repository extends JpaRepository）也当成 MyBatis Mapper 注册导致 bean 冲突。
@MapperScan(basePackages = "com.yqz.openblog", annotationClass = Mapper.class)
public class MyBatisPlusConfig {

    /**
     * 未注册分页插件时，selectPage 往往 total=0 但 records 仍有数据，导致前端文章总数错误。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}

