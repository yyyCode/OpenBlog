package com.yqz.openblog.category.dto;

import java.util.ArrayList;
import java.util.List;

public class CategoryTreeNodeResponse {

    private Long id;
    private String name;
    private Long parentId;
    private Integer sortOrder;
    private List<CategoryTreeNodeResponse> children = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public List<CategoryTreeNodeResponse> getChildren() {
        return children;
    }

    public void setChildren(List<CategoryTreeNodeResponse> children) {
        this.children = children;
    }
}
