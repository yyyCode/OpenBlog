package com.yqz.openblog.smallcompany.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "small_companies", indexes = {
        @Index(name = "idx_small_companies_status_sort", columnList = "status,sort_order,published_at")
})
public class SmallCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 32)
    private String type;

    @Column(name = "scale_min")
    private Integer scaleMin;

    @Column(name = "scale_max")
    private Integer scaleMax;

    @Column(length = 16)
    private String color;

    @Column(length = 64)
    private String logoMediaKey;

    @Column(length = 64)
    private String city;

    private Integer founded;

    @Column(length = 255)
    private String address;

    @Column(length = 255)
    private String business;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 128)
    private String website;

    @Column(nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SmallCompanyStatus status;

    @Column
    private Instant publishedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (sortOrder == null) sortOrder = 0;
        if (status == null) status = SmallCompanyStatus.DRAFT;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public SmallCompany() {}

    // --- getters / setters ---

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getScaleMin() {
        return scaleMin;
    }

    public void setScaleMin(Integer scaleMin) {
        this.scaleMin = scaleMin;
    }

    public Integer getScaleMax() {
        return scaleMax;
    }

    public void setScaleMax(Integer scaleMax) {
        this.scaleMax = scaleMax;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getLogoMediaKey() {
        return logoMediaKey;
    }

    public void setLogoMediaKey(String logoMediaKey) {
        this.logoMediaKey = logoMediaKey;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public Integer getFounded() {
        return founded;
    }

    public void setFounded(Integer founded) {
        this.founded = founded;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getBusiness() {
        return business;
    }

    public void setBusiness(String business) {
        this.business = business;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public SmallCompanyStatus getStatus() {
        return status;
    }

    public void setStatus(SmallCompanyStatus status) {
        this.status = status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
