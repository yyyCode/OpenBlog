package com.yqz.openblog.smallcompany.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SmallCompanyUpsertRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    @Size(max = 32)
    private String type;

    private Integer scaleMin;

    private Integer scaleMax;

    @Size(max = 16)
    private String color;

    /** 允许填完整媒体链接（如 https://…/api/v1/media/files/general/xxx.png），service 会归一化为 key 入库 */
    @Size(max = 512)
    private String logoMediaKey;

    @Size(max = 64)
    private String city;

    private Integer founded;

    @Size(max = 255)
    private String address;

    @Size(max = 255)
    private String business;

    @Size(max = 10000)
    private String description;

    @Size(max = 128)
    private String website;

    private Integer sortOrder;

    private String status;

    /** ISO-8601 可选 */
    private String publishedAt;

    public @NotBlank @Size(max = 120) String getName() { return name; }
    public void setName(@NotBlank @Size(max = 120) String name) { this.name = name; }

    public @Size(max = 32) String getType() { return type; }
    public void setType(@Size(max = 32) String type) { this.type = type; }

    public Integer getScaleMin() { return scaleMin; }
    public void setScaleMin(Integer scaleMin) { this.scaleMin = scaleMin; }

    public Integer getScaleMax() { return scaleMax; }
    public void setScaleMax(Integer scaleMax) { this.scaleMax = scaleMax; }

    public @Size(max = 16) String getColor() { return color; }
    public void setColor(@Size(max = 16) String color) { this.color = color; }

    public @Size(max = 512) String getLogoMediaKey() { return logoMediaKey; }
    public void setLogoMediaKey(@Size(max = 512) String logoMediaKey) { this.logoMediaKey = logoMediaKey; }

    public @Size(max = 64) String getCity() { return city; }
    public void setCity(@Size(max = 64) String city) { this.city = city; }

    public Integer getFounded() { return founded; }
    public void setFounded(Integer founded) { this.founded = founded; }

    public @Size(max = 255) String getAddress() { return address; }
    public void setAddress(@Size(max = 255) String address) { this.address = address; }

    public @Size(max = 255) String getBusiness() { return business; }
    public void setBusiness(@Size(max = 255) String business) { this.business = business; }

    public @Size(max = 10000) String getDescription() { return description; }
    public void setDescription(@Size(max = 10000) String description) { this.description = description; }

    public @Size(max = 128) String getWebsite() { return website; }
    public void setWebsite(@Size(max = 128) String website) { this.website = website; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPublishedAt() { return publishedAt; }
    public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }
}
