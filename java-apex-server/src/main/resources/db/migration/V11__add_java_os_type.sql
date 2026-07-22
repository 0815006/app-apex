-- =============================================
-- V11: 扩展 os_type 支持 JAVA_ACTUATOR 和 JAVA_JMX
-- 无需新增列，路径在后端代码中根据 osType 自动选择
-- =============================================
ALTER TABLE `monitor_machine`
    MODIFY COLUMN `os_type` VARCHAR(20) NOT NULL
    COMMENT 'WINDOWS、LINUX、MYSQL、JAVA_ACTUATOR 或 JAVA_JMX';
