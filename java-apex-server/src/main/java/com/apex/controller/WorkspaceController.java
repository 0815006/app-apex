package com.apex.controller;

import com.apex.common.EmpContext;
import com.apex.common.Result;
import com.apex.entity.AgentWorkspace;
import com.apex.service.AgentWorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 智能体工作空间 Controller。
 */
@Slf4j
@RestController
@RequestMapping("/api/workspace")
@RequiredArgsConstructor
public class WorkspaceController {

    private final AgentWorkspaceService workspaceService;

    /** 获取工作空间列表 */
    @GetMapping("/list")
    public Result<List<AgentWorkspace>> list() {
        log.info("[{}] 查询工作空间列表", EmpContext.getEmpNo());
        return Result.success(workspaceService.listAll());
    }

    /** 创建工作空间 */
    @PostMapping("/create")
    public Result<AgentWorkspace> create(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String dirName = body.get("dirName");
        String description = body.getOrDefault("description", "");
        log.info("[{}] 创建工作空间: name={}, dirName={}", EmpContext.getEmpNo(), name, dirName);
        return Result.success(workspaceService.create(name, dirName, description));
    }

    /** 更新工作空间 */
    @PutMapping("/{id}")
    public Result<AgentWorkspace> update(@PathVariable String id, @RequestBody Map<String, String> body) {
        String name = body.get("name");
        String description = body.getOrDefault("description", "");
        log.info("[{}] 更新工作空间: id={}, name={}", EmpContext.getEmpNo(), id, name);
        return Result.success(workspaceService.update(id, name, description));
    }

    /** 删除工作空间（不删物理目录） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        log.info("[{}] 删除工作空间: id={}", EmpContext.getEmpNo(), id);
        workspaceService.delete(id);
        return Result.success();
    }

    /** 查询磁盘上存在但未入库的子目录名，用于「导入已有目录」 */
    @GetMapping("/unregistered-dirs")
    public Result<List<String>> listUnregisteredDirs() {
        log.info("[{}] 查询未入库目录", EmpContext.getEmpNo());
        return Result.success(workspaceService.listUnregisteredDirs());
    }

    /** 导入已有磁盘目录为工作空间 */
    @PostMapping("/import")
    public Result<AgentWorkspace> importWorkspace(@RequestBody Map<String, String> body) {
        String dirName = body.get("dirName");
        String name = body.getOrDefault("name", dirName);
        String description = body.getOrDefault("description", "");
        log.info("[{}] 导入工作空间: dirName={}, name={}", EmpContext.getEmpNo(), dirName, name);
        return Result.success(workspaceService.importWorkspace(dirName, name, description));
    }

    /** 获取工作空间文件树 */
    @GetMapping("/{id}/tree")
    public Result<List<Map<String, Object>>> getTree(@PathVariable String id) {
        log.info("[{}] 查询文件树: workspaceId={}", EmpContext.getEmpNo(), id);
        return Result.success(workspaceService.getFileTree(id));
    }

    /** 读取工作空间内指定文件内容 */
    @GetMapping("/{id}/file")
    public Result<String> readFile(@PathVariable String id, @RequestParam String path) {
        log.info("[{}] 读取文件: workspaceId={}, path={}", EmpContext.getEmpNo(), id, path);
        return Result.success(workspaceService.readFile(id, path));
    }

    /** 删除工作空间内的文件或目录 */
    @DeleteMapping("/{id}/file")
    public Result<Void> deleteFile(@PathVariable String id, @RequestParam String path) {
        log.info("[{}] 删除文件/目录: workspaceId={}, path={}", EmpContext.getEmpNo(), id, path);
        workspaceService.deleteFileOrDir(id, path);
        return Result.success();
    }

    /** 重命名工作空间内的文件或目录 */
    @PutMapping("/{id}/rename")
    public Result<Void> renameFile(@PathVariable String id, @RequestBody Map<String, String> body) {
        String path = body.get("path");
        String newName = body.get("newName");
        log.info("[{}] 重命名: workspaceId={}, path={}, newName={}", EmpContext.getEmpNo(), id, path, newName);
        workspaceService.renameFile(id, path, newName);
        return Result.success();
    }

    /** 上传文件到工作空间指定目录 */
    @PostMapping("/{id}/upload")
    public Result<Void> uploadFile(@PathVariable String id,
                                   @RequestParam String path,
                                   @RequestParam("file") MultipartFile file) {
        log.info("[{}] 上传文件: workspaceId={}, dir={}, fileName={}", EmpContext.getEmpNo(), id, path,
                file.getOriginalFilename());
        workspaceService.uploadFile(id, path, file);
        return Result.success();
    }
}
