-- ============================================
-- V9: Agent 智能体功能升级
-- 日期: 2026-07-05
-- ============================================

-- 1. 升级 chat_session：新增 session_mode + workspace_id
ALTER TABLE `chat_session`
    ADD COLUMN `session_mode` VARCHAR(16) NOT NULL DEFAULT 'CHAT'
        COMMENT '会话模式：CHAT（纯对话）/ AGENT（智能体）',
    ADD COLUMN `workspace_id` VARCHAR(32) DEFAULT NULL
        COMMENT '关联的工作空间 ID（AGENT 模式时必选）';

-- 2. 升级 chat_message：新增工具调用相关字段
ALTER TABLE `chat_message`
    ADD COLUMN `tool_name` VARCHAR(64) DEFAULT NULL
        COMMENT '工具名称',
    ADD COLUMN `tool_call_id` VARCHAR(64) DEFAULT NULL
        COMMENT '工具调用唯一ID（大模型返回的 call_id）',
    ADD COLUMN `tool_status` VARCHAR(20) DEFAULT NULL
        COMMENT '工具执行状态：running/success/failed',
    ADD COLUMN `tool_calls_json` TEXT DEFAULT NULL
        COMMENT '原始 tool_calls JSON（assistant 消息时存完整数组）';

-- 3. 升级 llm_config：新增 is_agent_supported
ALTER TABLE `llm_config`
    ADD COLUMN `is_agent_supported` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '是否支持 Agent 模式（0=不支持，1=支持）';

-- 4. 新增 agent_workspace 表
CREATE TABLE `agent_workspace` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
    `name` VARCHAR(64) NOT NULL COMMENT '工作空间显示名称',
    `dir_name` VARCHAR(128) NOT NULL COMMENT '子目录名',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dir_name` (`dir_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体工作空间表';
