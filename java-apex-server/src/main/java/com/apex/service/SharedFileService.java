package com.apex.service;

import com.apex.common.BusinessException;
import com.apex.common.EmpContext;
import com.apex.entity.SharedFile;
import com.apex.mapper.SharedFileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 文件共享核心业务 Service。
 * 负责文件上传、列表查询、下载和删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SharedFileService {

    private final SharedFileMapper sharedFileMapper;

    @Value("${apex.file-share.storage-path}")
    private String storageRootPath;

    private static final DateTimeFormatter DATE_DIR_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * 上传文件。
     * 将文件保存到配置的存储目录（按月分子目录），并在数据库记录文件信息。
     *
     * @param file     上传的文件
     * @param uploadIp 上传人客户端 IP
     * @return 保存后的文件记录
     */
    @Transactional
    public SharedFile upload(MultipartFile file, String uploadIp) {
        if (file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new BusinessException("文件名不能为空");
        }

        // 构造存储路径：{storageRootPath}/{yyyy-MM}/{uuid}_{原始文件名}
        String dateDir = LocalDateTime.now().format(DATE_DIR_FORMATTER);
        String uniqueName = UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                + "_" + originalFileName;
        String relativePath = dateDir + "/" + uniqueName;

        Path targetPath = Paths.get(storageRootPath, relativePath);
        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("文件写入磁盘失败: {}", targetPath, e);
            throw new BusinessException("文件保存失败，请稍后重试");
        }

        // 记录到数据库
        SharedFile entity = new SharedFile();
        entity.setFileName(originalFileName);
        entity.setFileSize(file.getSize());
        entity.setStoragePath(relativePath);
        entity.setUploadEmpNo(EmpContext.getEmpNo());
        entity.setUploadIp(uploadIp);

        sharedFileMapper.insert(entity);
        log.info("[{}] 上传文件成功: {} -> {}", EmpContext.getEmpNo(), originalFileName, targetPath);
        return entity;
    }

    /**
     * 分页查询文件列表，按上传时间倒序。
     *
     * @param page     当前页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public Page<SharedFile> listFiles(int page, int pageSize) {
        Page<SharedFile> pageResult = new Page<>(page, pageSize);
        LambdaQueryWrapper<SharedFile> wrapper = new LambdaQueryWrapper<SharedFile>()
                .orderByDesc(SharedFile::getCreateTime);
        return sharedFileMapper.selectPage(pageResult, wrapper);
    }

    /**
     * 根据 ID 获取文件记录（用于下载）。
     *
     * @param id 文件记录 ID
     * @return 文件记录
     */
    public SharedFile getById(String id) {
        SharedFile file = sharedFileMapper.selectById(id);
        if (file == null) {
            throw new BusinessException(404, "文件不存在");
        }
        return file;
    }

    /**
     * 获取文件在磁盘上的完整物理路径。
     *
     * @param entity 文件实体
     * @return 磁盘完整路径
     */
    public Path getAbsolutePath(SharedFile entity) {
        Path absolutePath = Paths.get(storageRootPath, entity.getStoragePath());
        if (!Files.exists(absolutePath)) {
            throw new BusinessException(404, "文件已被物理删除");
        }
        return absolutePath;
    }

    /**
     * 删除文件记录及磁盘文件。
     *
     * @param id 文件记录 ID
     */
    @Transactional
    public void deleteById(String id) {
        SharedFile entity = getById(id);
        // 删除物理文件
        Path absolutePath = Paths.get(storageRootPath, entity.getStoragePath());
        try {
            Files.deleteIfExists(absolutePath);
            log.info("[{}] 删除磁盘文件: {}", EmpContext.getEmpNo(), absolutePath);
        } catch (IOException e) {
            log.warn("[{}] 删除磁盘文件失败: {}", EmpContext.getEmpNo(), absolutePath, e);
        }
        // 删除数据库记录
        sharedFileMapper.deleteById(id);
        log.info("[{}] 删除文件记录: id={}, fileName={}", EmpContext.getEmpNo(), id, entity.getFileName());
    }

    /**
     * 列出所有文件（不分页，供前端简单场景使用）。
     */
    public List<SharedFile> listAll() {
        LambdaQueryWrapper<SharedFile> wrapper = new LambdaQueryWrapper<SharedFile>()
                .orderByDesc(SharedFile::getCreateTime);
        return sharedFileMapper.selectList(wrapper);
    }
}
