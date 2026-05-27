package com.apex.model;

import lombok.Data;

import java.util.List;

/**
 * 批量排序请求 DTO。
 */
@Data
public class SortOrderDTO {

    /** 排序项列表 */
    private List<SortItem> items;

    @Data
    public static class SortItem {
        /** 节点 ID */
        private String id;
        /** 排序权重 */
        private Integer sortOrder;
    }
}
