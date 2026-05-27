package com.apex.controller;

import com.apex.common.EmpContext;
import com.apex.common.Result;
import com.apex.entity.SharedFile;
import com.apex.service.SharedFileService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件共享 Controller。
 * 提供文件上传、列表、下载、删除等 REST API。
 */
@Slf4j
@RestController
@RequestMapping("/api/file-share")
@RequiredArgsConstructor
public class FileShareController {

    private final SharedFileService sharedFileService;

    /**
     * 上传文件。
     */
    @PostMapping("/upload")
    public Result<SharedFile> upload(@RequestParam("file") MultipartFile file,
                                     HttpServletRequest request) {
        String uploadIp = extractClientIp(request);
        log.info("[{}] 上传文件: fileName={}, size={}, ip={}",
                EmpContext.getEmpNo(), file.getOriginalFilename(), file.getSize(), uploadIp);
        return Result.success(sharedFileService.upload(file, uploadIp));
    }

    /**
     * 获取文件列表（分页）。
     */
    @GetMapping("/list")
    public Result<Page<SharedFile>> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int pageSize) {
        log.info("[{}] 查询文件列表: page={}, pageSize={}", EmpContext.getEmpNo(), page, pageSize);
        return Result.success(sharedFileService.listFiles(page, pageSize));
    }

    /**
     * 获取全部文件列表（不分页）。
     */
    @GetMapping("/all")
    public Result<List<SharedFile>> all() {
        log.info("[{}] 查询全部文件列表", EmpContext.getEmpNo());
        return Result.success(sharedFileService.listAll());
    }

    /**
     * 下载文件。
     * 返回 ResponseEntity<Resource> 以支持流式下载和正确的内容类型。
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable String id) {
        log.info("[{}] 下载文件: id={}", EmpContext.getEmpNo(), id);
        SharedFile entity = sharedFileService.getById(id);
        Resource resource = new FileSystemResource(sharedFileService.getAbsolutePath(entity));

        // 处理中文文件名编码
        String encodedFileName = URLEncoder.encode(entity.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFileName)
                .body(resource);
    }

    /**
     * 删除文件。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        log.info("[{}] 删除文件: id={}", EmpContext.getEmpNo(), id);
        sharedFileService.deleteById(id);
        return Result.success();
    }

    /**
     * 从 HttpServletRequest 提取客户端真实 IP。
     */
    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            // 多层代理取第一个
            ip = ip.split(",")[0].trim();
            return ip;
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}
