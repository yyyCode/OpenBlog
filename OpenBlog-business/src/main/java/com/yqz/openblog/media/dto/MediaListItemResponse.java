package com.yqz.openblog.media.dto;

import java.time.Instant;

public class MediaListItemResponse {

    private String key;
    private String url;
    private String thumbUrl;
    private String contentType;
    private Long size;
    private Integer width;
    private Integer height;
    private Integer thumbWidth;
    private Integer thumbHeight;
    private Instant createdAt;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getThumbUrl() { return thumbUrl; }
    public void setThumbUrl(String thumbUrl) { this.thumbUrl = thumbUrl; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }

    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }

    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }

    public Integer getThumbWidth() { return thumbWidth; }
    public void setThumbWidth(Integer thumbWidth) { this.thumbWidth = thumbWidth; }

    public Integer getThumbHeight() { return thumbHeight; }
    public void setThumbHeight(Integer thumbHeight) { this.thumbHeight = thumbHeight; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
