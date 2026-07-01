-- =============================================
-- V6: 将端口定制表重构为通用指标定制表
-- 支持用户从 Exporter 全量指标中任意勾选定制
-- =============================================

-- 删除旧的端口定制表
DROP TABLE IF EXISTS `monitor_app_port`;

-- 新建通用指标定制表
CREATE TABLE `monitor_custom_metric` (
  `id` int NOT NULL AUTO_INCREMENT,
  `machine_id` int NOT NULL COMMENT '关联机器ID',
  `metric_key` varchar(300) NOT NULL COMMENT '指标唯一标识（纯指标名或含标签的完整key，如 node_memory_MemTotal_bytes 或 tcp_listen{port="3306"}）',
  `metric_name` varchar(200) NOT NULL COMMENT '纯指标名，用于后端匹配（如 node_memory_MemTotal_bytes）',
  `display_name` varchar(100) NOT NULL COMMENT '用户自定义展示别名（如"MySQL数据库"）',
  `category` varchar(50) DEFAULT NULL COMMENT '指标分类（cpu/memory/disk/network/service/system/runtime/other）',
  `is_visible` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否在容量监控卡片中展示 1可见 0隐藏',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_machine_id` (`machine_id`),
  KEY `idx_machine_metric` (`machine_id`, `metric_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控-用户定制指标表';
