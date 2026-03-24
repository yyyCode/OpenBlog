package com.yqz.openblog;

import com.yqz.openblog.config.AuthSecurityProperties;
import com.yqz.openblog.config.CorsProperties;
import com.yqz.openblog.config.SiteProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        SiteProperties.class,
        CorsProperties.class,
        AuthSecurityProperties.class
})
public class OpenBlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenBlogApplication.class, args);
    }

}
