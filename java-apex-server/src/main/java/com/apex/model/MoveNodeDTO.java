package com.apex.model;

import lombok.Data;

/**
 * 移动节点请求 DTO。
 */
@Data
public class MoveNodeDTO {

    /** 目标父节点 ID，「0」表示根目录 */
    private String newParentId;

    /** 目标排序位置 */
    private Integer newSortOrder;
}
