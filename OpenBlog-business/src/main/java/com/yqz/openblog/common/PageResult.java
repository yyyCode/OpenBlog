package com.yqz.openblog.common;

import java.util.List;

public class PageResult<T> {
    private List<T> items;
    private int page;
    private int size;
    private long total;

    public PageResult(List<T> items, int page, int size, long total) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.total = total;
    }

    public List<T> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotal() {
        return total;
    }
}
