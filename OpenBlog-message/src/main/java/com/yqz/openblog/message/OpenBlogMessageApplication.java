package com.yqz.openblog.message;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDubbo
@MapperScan("com.yqz.openblog.message.repo")
public class OpenBlogMessageApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenBlogMessageApplication.class, args);
    }
}
