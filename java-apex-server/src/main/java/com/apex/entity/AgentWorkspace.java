package com.apex.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体工作空间实体。
 */
@Data
@TableName("agent_workspace")
public class AgentWorkspace {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 工作空间显示名称 */
    private String name;

    /** 子目录名 */
    private String dirName;

    /** 描述 */
    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
