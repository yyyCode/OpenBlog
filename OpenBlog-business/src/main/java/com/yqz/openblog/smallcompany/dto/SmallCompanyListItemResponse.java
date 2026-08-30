package com.yqz.openblog.smallcompany.dto;

import com.yqz.openblog.smallcompany.entity.SmallCompanyStatus;
import java.time.Instant;

public class SmallCompanyListItemResponse {
    private Long id;
    private String name;
    private String type;
    private Integer scaleMin;
    private Integer scaleMax;
    private String color;
    private String logoMediaKey;
    private String city;
    private Integer founded;
    private Integer sortOrder;
    private SmallCompanyStatus status;
    private Instant publishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Integer getScaleMin() { return scaleMin; }
    public void setScaleMin(Integer scaleMin) { this.scaleMin = scaleMin; }

    public Integer getScaleMax() { return scaleMax; }
    public void setScaleMax(Integer scaleMax) { this.scaleMax = scaleMax; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getLogoMediaKey() { return logoMediaKey; }
    public void setLogoMediaKey(String logoMediaKey) { this.logoMediaKey = logoMediaKey; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Integer getFounded() { return founded; }
    public void setFounded(Integer founded) { this.founded = founded; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public SmallCompanyStatus getStatus() { return status; }
    public void setStatus(SmallCompanyStatus status) { this.status = status; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
