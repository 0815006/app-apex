-- =============================================
-- 轻量级自建服务器监控系统 — 核心表
-- 数据源：node_exporter (Linux, :9100) / windows_exporter (Windows, :9182)
-- =============================================

-- 1. 机器监控主表
CREATE TABLE `monitor_machine` (
  `id` int NOT NULL AUTO_INCREMENT,
  `machine_name` varchar(100) NOT NULL COMMENT '机器别名',
  `ip` varchar(50) NOT NULL COMMENT '机器IP',
  `os_type` varchar(20) NOT NULL COMMENT 'WINDOWS 或 LINUX',
  `exporter_port` int NOT NULL DEFAULT '9100' COMMENT 'Exporter端口',
  `refresh_interval` int NOT NULL DEFAULT '3' COMMENT '前端刷新频率(秒)',
  `is_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否开启监控 1开启 0关闭',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控-机器主表';

-- 2. 自建应用/端口精细化配置表
CREATE TABLE `monitor_app_port` (
  `id` int NOT NULL AUTO_INCREMENT,
  `machine_id` int NOT NULL COMMENT '关联机器ID',
  `app_name` varchar(100) NOT NULL COMMENT '应用/服务别名',
  `port` int NOT NULL COMMENT '具体监听端口',
  `is_visible` tinyint(1) NOT NULL DEFAULT '1' COMMENT '日常大屏是否可见 1可见 0隐藏',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_machine_id` (`machine_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控-应用端口配置表';

-- 3. 采样任务控制表
CREATE TABLE `monitor_sample_task` (
  `id` int NOT NULL AUTO_INCREMENT,
  `machine_id` int NOT NULL COMMENT '关联机器ID',
  `task_name` varchar(100) NOT NULL COMMENT '任务名称/备注',
  `start_time` datetime NOT NULL COMMENT '任务开始采集时间',
  `end_time` datetime NOT NULL COMMENT '任务结束采集时间',
  `collect_interval` int NOT NULL DEFAULT '3' COMMENT '采集频率(秒)',
  `status` varchar(20) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING(等待), RUNNING(采集中), FINISHED(已结束)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控-采样任务控制表';

-- 4. 采样历史流水数据表
CREATE TABLE `monitor_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` int NOT NULL COMMENT '关联任务ID',
  `cpu_usage` float NOT NULL COMMENT 'CPU使用率(%)',
  `mem_usage` float NOT NULL COMMENT '内存使用率(%)',
  `disk_usage` float NOT NULL COMMENT '主磁盘使用率(%)',
  `record_time` datetime NOT NULL COMMENT '记录生成时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_time` (`task_id`, `record_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控-采样历史流水表';
