package com.yqz.openblog;

import com.yqz.openblog.config.AuthSecurityProperties;
import com.yqz.openblog.config.CorsProperties;
import com.yqz.openblog.config.SiteProperties;
import com.yqz.openblog.notification.NotificationProperties;
import com.yqz.openblog.seo.SeoProperties;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 显式声明组件扫描（覆盖 @SpringBootApplication 的默认扫描）：
 * - basePackages 与默认一致；额外排除 com.yqz.openblog.common 包——
 *   该包的异常处理器改由 OpenBlog-common 的自动装配（CommonAutoConfiguration）注册。
 * - 显式 @ComponentScan 不会自动带上 @SpringBootApplication 默认的两个过滤，
 *   因此手动补回 TypeExcludeFilter / AutoConfigurationExcludeFilter，保证
 *   framework 模块下的 @AutoConfiguration 类不被组件扫描重复注册。
 */
@SpringBootApplication
@EnableDubbo
@EnableScheduling
@EnableConfigurationProperties({
        SiteProperties.class,
        CorsProperties.class,
        AuthSecurityProperties.class,
        SeoProperties.class,
        NotificationProperties.class
})
@ComponentScan(
        basePackages = "com.yqz.openblog",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.yqz\\.openblog\\.common\\..*")
        })
public class OpenBlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenBlogApplication.class, args);
    }

}
