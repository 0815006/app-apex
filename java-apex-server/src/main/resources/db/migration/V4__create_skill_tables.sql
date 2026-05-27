-- =============================================
-- AI 工具箱 - Skill 技能体系表
-- 支持三种类型：prompt(提示词)、agent(工具调用)、workflow(工作流)
-- 现阶段仅实现 prompt 类型
-- =============================================

-- 1. AI 技能主表
CREATE TABLE `ai_skill` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键（雪花ID）',
    `name` VARCHAR(100) NOT NULL COMMENT '技能名称',
    `icon` VARCHAR(100) DEFAULT NULL COMMENT '图标类名或URL',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '功能简介',
    `type` VARCHAR(20) NOT NULL DEFAULT 'prompt' COMMENT '核心类型：prompt(纯提示词), agent(带工具调用), workflow(工作流)',
    `system_prompt` TEXT COMMENT '给大模型的 system 角色内容（type为 prompt 或 agent 时有效）',
    `temperature` DECIMAL(3,2) DEFAULT 0.70 COMMENT '采样温度',
    `workflow_id` VARCHAR(64) DEFAULT NULL COMMENT '关联的自定义工作流ID（type为 workflow 时有效）',
    `status` TINYINT(1) DEFAULT 1 COMMENT '状态：1启用，0禁用',
    `sort_order` INT(11) DEFAULT 0 COMMENT '排序序号',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI工具箱-技能主表';

-- 2. AI 原子工具定义表（后续 agent 阶段使用）
CREATE TABLE `ai_tool` (
    `id` VARCHAR(64) NOT NULL COMMENT '工具唯一标识，传给大模型的 function_name',
    `name` VARCHAR(100) NOT NULL COMMENT '工具人类化名称',
    `description` TEXT NOT NULL COMMENT '工具功能描述（大模型据此决定是否调用）',
    `declaration_json` TEXT NOT NULL COMMENT '符合 OpenAI Function Calling 标准的 JSON 声明（入参、出参定义）',
    `adapter_bean` VARCHAR(100) DEFAULT NULL COMMENT '后端真正执行该工具的 Java 映射类/Bean名',
    `status` TINYINT(1) DEFAULT 1 COMMENT '状态：1启用，0禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI工具箱-原子工具定义表';

-- 3. 技能-工具绑定关系表（多对多，后续 agent 阶段使用）
CREATE TABLE `ai_skill_tool_relation` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `skill_id` VARCHAR(64) NOT NULL COMMENT '技能ID',
    `tool_id` VARCHAR(64) NOT NULL COMMENT '工具ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_skill_tool` (`skill_id`, `tool_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI技能-工具绑定关系表';
