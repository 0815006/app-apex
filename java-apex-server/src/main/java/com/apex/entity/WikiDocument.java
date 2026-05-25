package com.apex.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Wiki文档实体类。
 * type: 1-文件夹, 2-具体文档
 * parent_id: 父级ID，"0"表示根目录
 */
@Data
@TableName("wiki_document")
public class WikiDocument {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String title;

    private String content;

    private Integer type;

    private String parentId;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
