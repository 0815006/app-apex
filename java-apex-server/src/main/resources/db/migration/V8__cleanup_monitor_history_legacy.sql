-- =============================================
-- V8: 清理 monitor_history 旧版固定列，完全迁移到 JSON 动态存储
-- 说明：V7 引入了 metric_values JSON 列，旧的 cpu_usage/mem_usage/disk_usage 列已冗余
-- =============================================

-- 1. 丢弃旧版固定列
ALTER TABLE `monitor_history`
  DROP COLUMN `cpu_usage`,
  DROP COLUMN `mem_usage`,
  DROP COLUMN `disk_usage`;

-- 2. 优化索引：新增 metric_values 无需额外索引，JSON 列按需查询
--    保留 idx_task_time 即可满足按任务+时间范围查询
