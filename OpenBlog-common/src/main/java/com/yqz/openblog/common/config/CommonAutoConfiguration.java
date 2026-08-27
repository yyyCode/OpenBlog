package com.yqz.openblog.common.config;

import com.yqz.openblog.common.GlobalExceptionHandler;
import com.yqz.openblog.common.SecurityExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * OpenBlog-common 自动装配：注册统一异常处理器。
 * 注意：调用方组件扫描应排除 com.yqz.openblog.common.*，避免与自动装配重复注册。
 */
@AutoConfiguration
@ConditionalOnWebApplication
public class CommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.security.access.AccessDeniedException")
    @ConditionalOnMissingBean(SecurityExceptionHandler.class)
    public SecurityExceptionHandler securityExceptionHandler() {
        return new SecurityExceptionHandler();
    }
}
