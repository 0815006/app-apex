package com.apex.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Wiki 树节点 VO，用于前端 Tree 组件渲染。
 */
public record WikiNodeVO(
        String id,
        String title,
        Integer type,
        String parentId,
        LocalDateTime updateTime,
        List<WikiNodeVO> children
) {

    /**
     * 构建叶子节点（无子节点）。
     */
    public WikiNodeVO(String id, String title, Integer type, String parentId, LocalDateTime updateTime) {
        this(id, title, type, parentId, updateTime, null);
    }
}
