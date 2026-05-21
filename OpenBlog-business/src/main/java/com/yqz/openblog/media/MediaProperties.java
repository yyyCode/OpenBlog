package com.yqz.openblog.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "openblog.storage")
public class MediaProperties {
    private String rootPath;
    private String publicBaseUrl;
    private int thumbLongEdge;

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public int getThumbLongEdge() {
        return thumbLongEdge;
    }

    public void setThumbLongEdge(int thumbLongEdge) {
        this.thumbLongEdge = thumbLongEdge;
    }
}

