CREATE TABLE `wiki_document` (
    `id` VARCHAR(32) NOT NULL COMMENT '唯一主键（雪花ID）',
    `title` VARCHAR(255) NOT NULL COMMENT '文档或文件夹标题',
    `content` LONGTEXT COMMENT 'Markdown原始内容（文件夹此项为空）',
    `type` TINYINT DEFAULT 2 COMMENT '节点类型: 1-文件夹, 2-具体文档',
    `parent_id` VARCHAR(32) DEFAULT '0' COMMENT '父级ID，0表示根目录',
    `sort_order` INT DEFAULT 0 COMMENT '同层级排序权重，数字越小越靠前',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_title` (`title`) COMMENT '标题唯一，确保双链能精准定位'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Wiki文档主表';
