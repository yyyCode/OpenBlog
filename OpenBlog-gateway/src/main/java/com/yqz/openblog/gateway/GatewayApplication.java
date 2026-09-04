package com.yqz.openblog.gateway;

import com.yqz.openblog.common.config.CommonAutoConfiguration;
import com.yqz.openblog.gateway.config.DeviceTokenProperties;
import com.yqz.openblog.gateway.config.GatewayProperties;
import com.yqz.openblog.gateway.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * OpenBlog API 网关。
 * 排除 CommonAutoConfiguration：其 GlobalExceptionHandler 是 servlet 风格 @RestControllerAdvice，
 * 与 WebFlux 的 ErrorWebExceptionHandler 冲突；仅复用 common 的 ApiResponse POJO。
 */
@SpringBootApplication(exclude = CommonAutoConfiguration.class)
@EnableConfigurationProperties({GatewayProperties.class, JwtProperties.class, DeviceTokenProperties.class})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
