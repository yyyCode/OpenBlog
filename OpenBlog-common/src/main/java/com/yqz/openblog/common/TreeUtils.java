package com.yqz.openblog.common;

import java.util.*;
import java.util.function.Function;

/**
 * 树形结构通用工具方法。CategoryService 和 MediaFolderService 共用。
 */
public final class TreeUtils {

    private TreeUtils() {
    }

    /**
     * 按 ID 建立索引 Map。
     */
    public static <T> Map<Long, T> indexById(List<T> list, Function<T, Long> idGetter) {
        Map<Long, T> map = new HashMap<>();
        for (T item : list) {
            map.put(idGetter.apply(item), item);
        }
        return map;
    }

    /**
     * 从指定节点向上追溯，构建路径名列表（从根到当前节点）。
     */
    public static <T> List<String> buildPathNames(Long nodeId,
                                                   Map<Long, T> byId,
                                                   Function<T, Long> parentIdGetter,
                                                   Function<T, String> nameGetter) {
        List<String> path = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Long current = nodeId;
        while (current != null && visited.add(current)) {
            T node = byId.get(current);
            if (node == null) {
                break;
            }
            path.add(0, nameGetter.apply(node));
            current = parentIdGetter.apply(node);
        }
        return path;
    }

    /**
     * 递归收集所有子孙节点 ID（含自身）。
     */
    public static void collectDescendants(Long id, Map<Long, List<Long>> childrenMap, Set<Long> out) {
        if (id == null || !out.add(id)) {
            return;
        }
        for (Long childId : childrenMap.getOrDefault(id, List.of())) {
            collectDescendants(childId, childrenMap, out);
        }
    }

    /**
     * 构建 parentId → children 映射。
     */
    public static Map<Long, List<Long>> buildChildrenMap(List<Long> ids, Function<Long, Long> parentIdGetter) {
        Map<Long, List<Long>> map = new HashMap<>();
        for (Long id : ids) {
            Long parentId = parentIdGetter.apply(id);
            if (parentId != null) {
                map.computeIfAbsent(parentId, k -> new ArrayList<>()).add(id);
            }
        }
        return map;
    }

    /**
     * 检查 nodeId 是否为 ancestorId 的后代。
     */
    public static <T> boolean isDescendant(Long ancestorId, Long nodeId, Map<Long, T> byId, Function<T, Long> parentIdGetter) {
        Long current = nodeId;
        while (current != null) {
            if (current.equals(ancestorId)) {
                return true;
            }
            T node = byId.get(current);
            current = node == null ? null : parentIdGetter.apply(node);
        }
        return false;
    }
}
