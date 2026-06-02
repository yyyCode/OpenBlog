package com.yqz.openblog.seo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "openblog.seo")
public class SeoProperties {

    private String siteUrl = "https://www.wecode.xin";
    private String siteName = "OpenBlog";
    private String siteDescription = "WeCode 技术博客";
    private String baiduPushToken;
    private boolean baiduPushEnabled = false;
}
