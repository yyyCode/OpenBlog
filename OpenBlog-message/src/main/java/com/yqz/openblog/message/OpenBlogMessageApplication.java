package com.yqz.openblog.message;

import com.yqz.openblog.notification.NotificationProperties;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * message 即「统一通知服务」。
 * 显式组件扫描覆盖 {@link com.yqz.openblog.notification} 包（迁入的编排层不在默认扫描范围
 * com.yqz.openblog.message 下）；MapperScan 额外包含 outbox mapper；@EnableScheduling 供 OutboxRelay。
 */
@SpringBootApplication
@EnableDubbo
@EnableScheduling
@MapperScan(basePackages = {"com.yqz.openblog.message.repo", "com.yqz.openblog.notification"}, annotationClass = Mapper.class)
@EnableConfigurationProperties({NotificationProperties.class})
@ComponentScan(
        basePackages = {"com.yqz.openblog.message", "com.yqz.openblog.notification"},
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
        })
public class OpenBlogMessageApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenBlogMessageApplication.class, args);
    }
}
