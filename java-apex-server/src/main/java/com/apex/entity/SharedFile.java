package com.apex.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件共享实体类。
 * 记录用户上传的共享文件信息，包含原始文件名、大小、磁盘存储路径、上传人工号及 IP。
 */
@Data
@TableName("shared_file")
public class SharedFile {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 文件原始名称（含扩展名） */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 磁盘上的物理存储路径（相对于配置的存储根目录） */
    private String storagePath;

    /** 上传人员工号 */
    private String uploadEmpNo;

    /** 上传人客户端 IP 地址 */
    private String uploadIp;

    /** 上传时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
