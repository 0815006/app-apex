-- =============================================
-- 文件共享核心表
-- 记录所有用户上传文件的信息
-- =============================================

CREATE TABLE `shared_file` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
    `file_name` VARCHAR(500) NOT NULL COMMENT '文件原始名称（含扩展名）',
    `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    `storage_path` VARCHAR(1000) NOT NULL COMMENT '磁盘上的物理存储路径（相对于配置的存储根目录）',
    `upload_emp_no` VARCHAR(7) NOT NULL COMMENT '上传人员工号',
    `upload_ip` VARCHAR(45) NOT NULL COMMENT '上传人客户端 IP 地址（支持 IPv6）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_upload_emp_no` (`upload_emp_no`) COMMENT '按上传人查询',
    INDEX `idx_create_time` (`create_time` DESC) COMMENT '按上传时间倒序查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件共享表';
