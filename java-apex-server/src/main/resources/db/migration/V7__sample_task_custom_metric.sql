-- =============================================
-- V7: 采样任务支持关联定制指标，历史数据改为 JSON 动态存储
-- =============================================

-- 1. 采样任务表新增指标关联列
ALTER TABLE `monitor_sample_task`
  ADD COLUMN `metric_ids` varchar(500) DEFAULT NULL
    COMMENT '关联的定制指标ID列表，JSON数组如 [1,3,5]，为空则兼容旧逻辑(CPU/MEM/DISK)';

-- 2. 历史表新增 JSON 列存储动态指标值
ALTER TABLE `monitor_history`
  ADD COLUMN `metric_values` json DEFAULT NULL
    COMMENT '指标值快照，如 {"cpu_usage":45.2, "mem_usage":62.1, "disk_usage":38.7, "tcp_listen{port=\"3306\"}":1}';
