-- =============================================
-- AI 聊天助手核心表
-- 多租户隔离：所有表通过 user_id 隔离数据
-- =============================================

-- 1. 大模型配置表
CREATE TABLE `llm_config` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
    `user_id` VARCHAR(7) NOT NULL COMMENT '用户工号（多租户隔离）',
    `config_name` VARCHAR(100) NOT NULL COMMENT '配置别名（如：个人DeepSeek、公司GPT-4）',
    `api_url` VARCHAR(500) NOT NULL COMMENT '大模型 Base URL（如 https://api.openai.com/v1）',
    `api_key` VARCHAR(500) NOT NULL COMMENT 'API 密钥',
    `model_name` VARCHAR(100) NOT NULL COMMENT '模型名称（如 gpt-4o、deepseek-chat）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`) COMMENT '按用户查询配置列表'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大模型配置表';

-- 2. 对话会话表
CREATE TABLE `chat_session` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
    `user_id` VARCHAR(7) NOT NULL COMMENT '用户工号（多租户隔离）',
    `title` VARCHAR(200) NOT NULL DEFAULT '新对话' COMMENT '会话标题（默认新对话，首条消息后更新为前10字）',
    `config_id` VARCHAR(32) NOT NULL COMMENT '绑定的 LLM 配置 ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（每次对话更新，用于倒序排序）',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id_update` (`user_id`, `update_time` DESC) COMMENT '按用户+更新时间倒序查询会话列表'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话会话表';

-- 3. 消息明细表
CREATE TABLE `chat_message` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
    `session_id` VARCHAR(32) NOT NULL COMMENT '所属会话 ID',
    `role` VARCHAR(20) NOT NULL COMMENT '角色：user（用户）或 assistant（AI）',
    `content` LONGTEXT NOT NULL COMMENT '消息文本内容',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (`id`),
    INDEX `idx_session_id` (`session_id`) COMMENT '按会话查询消息历史',
    INDEX `idx_session_create` (`session_id`, `create_time` ASC) COMMENT '按会话+时间正序加载历史消息'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息明细表';
