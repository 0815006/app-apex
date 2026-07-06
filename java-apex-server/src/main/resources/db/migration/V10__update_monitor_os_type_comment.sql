-- =============================================
-- V10: 扩展 os_type 支持 MySQL (mysqld_exporter)
-- =============================================
ALTER TABLE `monitor_machine`
    MODIFY COLUMN `os_type` VARCHAR(20) NOT NULL COMMENT 'WINDOWS、LINUX 或 MYSQL';
