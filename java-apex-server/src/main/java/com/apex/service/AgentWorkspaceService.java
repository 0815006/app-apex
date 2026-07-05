package com.apex.service;

import com.apex.entity.AgentWorkspace;
import com.apex.mapper.AgentWorkspaceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 智能体工作空间管理 Service。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentWorkspaceService {

    @Value("${apex.agent.workspace.root-dir}")
    private String rootDir;

    private final AgentWorkspaceMapper workspaceMapper;

    /**
     * 获取工作空间列表。
     */
    public List<AgentWorkspace> listAll() {
        return workspaceMapper.selectList(
                new LambdaQueryWrapper<AgentWorkspace>()
                        .orderByDesc(AgentWorkspace::getUpdateTime)
        );
    }

    /**
     * 创建工作空间（自动在磁盘根目录下 mkdir）。
     */
    @Transactional
    public AgentWorkspace create(String name, String dirName, String description) {
        // 校验 dirName 唯一性（直接用用户输入的目录名，同名冲突返回错误）
        Long count = workspaceMapper.selectCount(
                new LambdaQueryWrapper<AgentWorkspace>()
                        .eq(AgentWorkspace::getDirName, dirName)
        );
        if (count > 0) {
            throw new IllegalArgumentException("子目录名已存在: " + dirName);
        }

        // 在磁盘上创建目录
        Path dirPath = Paths.get(rootDir, dirName);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            throw new RuntimeException("创建磁盘目录失败: " + dirPath, e);
        }

        AgentWorkspace ws = new AgentWorkspace();
        ws.setName(name);
        ws.setDirName(dirName);
        ws.setDescription(description);
        workspaceMapper.insert(ws);
        log.info("创建工作空间: name={}, dir={}", name, dirName);
        return ws;
    }

    /**
     * 更新工作空间信息（名称、描述）。
     */
    @Transactional
    public AgentWorkspace update(String id, String name, String description) {
        AgentWorkspace ws = workspaceMapper.selectById(id);
        if (ws == null) {
            throw new IllegalArgumentException("工作空间不存在: " + id);
        }
        ws.setName(name);
        ws.setDescription(description);
        workspaceMapper.updateById(ws);
        return ws;
    }

    /**
     * 删除工作空间（不删物理目录）。
     */
    @Transactional
    public void delete(String id) {
        AgentWorkspace ws = workspaceMapper.selectById(id);
        if (ws == null) {
            throw new IllegalArgumentException("工作空间不存在: " + id);
        }
        workspaceMapper.deleteById(id);
        log.info("删除工作空间: name={}, dir={}", ws.getName(), ws.getDirName());
    }

    /**
     * 查询磁盘上存在但未入库的子目录名列表。
     */
    public List<String> listUnregisteredDirs() {
        // 1. 获取所有已入库的 dirName
        Set<String> registered = workspaceMapper.selectList(null).stream()
                .map(AgentWorkspace::getDirName)
                .collect(Collectors.toSet());

        // 2. 扫描磁盘根目录下的子目录
        File root = new File(rootDir);
        if (!root.exists() || !root.isDirectory()) {
            return List.of();
        }

        File[] children = root.listFiles(File::isDirectory);
        if (children == null) {
            return List.of();
        }

        return Arrays.stream(children)
                .map(File::getName)
                .filter(name -> !name.startsWith(".") && !registered.contains(name))
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 导入已有磁盘目录为工作空间（不创建磁盘目录）。
     */
    @Transactional
    public AgentWorkspace importWorkspace(String dirName, String name, String description) {
        // 校验磁盘目录存在
        Path dirPath = Paths.get(rootDir, dirName);
        if (!Files.isDirectory(dirPath)) {
            throw new IllegalArgumentException("目录不存在: " + dirName);
        }

        // 校验 dirName 未被注册
        Long count = workspaceMapper.selectCount(
                new LambdaQueryWrapper<AgentWorkspace>()
                        .eq(AgentWorkspace::getDirName, dirName)
        );
        if (count > 0) {
            throw new IllegalArgumentException("该目录已注册为工作空间: " + dirName);
        }

        AgentWorkspace ws = new AgentWorkspace();
        ws.setName(name);
        ws.setDirName(dirName);
        ws.setDescription(description);
        workspaceMapper.insert(ws);
        log.info("导入工作空间: name={}, dir={}", name, dirName);
        return ws;
    }

    // ===================== 文件树 =====================

    /**
     * 获取工作空间的目录树 JSON。
     */
    public List<Map<String, Object>> getFileTree(String workspaceId) {
        Path wsRoot = resolveWsRoot(workspaceId);
        if (!Files.exists(wsRoot)) {
            return List.of();
        }
        try {
            return buildTree(wsRoot, wsRoot);
        } catch (IOException e) {
            throw new RuntimeException("读取文件树失败", e);
        }
    }

    private List<Map<String, Object>> buildTree(Path root, Path currentDir) throws IOException {
        List<Map<String, Object>> nodes = new ArrayList<>();
        try (var stream = Files.list(currentDir)) {
            List<Path> sorted = stream.sorted().collect(Collectors.toList());
            for (Path path : sorted) {
                String name = path.getFileName().toString();
                if (name.startsWith(".")) continue; // 跳过隐藏文件

                Map<String, Object> node = new LinkedHashMap<>();
                node.put("name", name);
                node.put("path", root.relativize(path).toString().replace("\\", "/"));

                if (Files.isDirectory(path)) {
                    node.put("type", "dir");
                    node.put("children", buildTree(root, path));
                } else {
                    node.put("type", "file");
                }
                nodes.add(node);
            }
        }
        return nodes;
    }

    /**
     * 读取工作空间内指定文件内容。
     */
    public String readFile(String workspaceId, String relativePath) {
        Path filePath = resolvePath(workspaceId, relativePath);
        try {
            if (Files.size(filePath) > 2 * 1024 * 1024) {
                throw new IllegalArgumentException("文件超过 2MB 读取上限");
            }
            return Files.readString(filePath);
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + relativePath, e);
        }
    }

    // ===================== 文件/目录操作 =====================

    /**
     * 删除工作空间内的文件或空目录（目录非空时递归删除）。
     */
    public void deleteFileOrDir(String workspaceId, String relativePath) {
        Path target = resolvePath(workspaceId, relativePath);
        if (Files.notExists(target)) {
            throw new IllegalArgumentException("路径不存在: " + relativePath);
        }
        try {
            if (Files.isDirectory(target)) {
                // 递归删除目录
                try (Stream<Path> walk = Files.walk(target)) {
                    walk.sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try { Files.delete(p); } catch (IOException ignored) {}
                            });
                }
            } else {
                Files.delete(target);
            }
            log.info("删除文件/目录: workspaceId={}, path={}", workspaceId, relativePath);
        } catch (IOException e) {
            throw new RuntimeException("删除失败: " + relativePath, e);
        }
    }

    /**
     * 重命名工作空间内的文件或目录。
     */
    public void renameFile(String workspaceId, String relativePath, String newName) {
        Path target = resolvePath(workspaceId, relativePath);
        if (Files.notExists(target)) {
            throw new IllegalArgumentException("路径不存在: " + relativePath);
        }
        if (newName.contains("/") || newName.contains("\\")) {
            throw new IllegalArgumentException("新名称不能包含路径分隔符");
        }
        if (newName.startsWith(".")) {
            throw new IllegalArgumentException("新名称不能以点开头");
        }
        Path newPath = target.resolveSibling(newName);
        if (Files.exists(newPath)) {
            throw new IllegalArgumentException("目标名称已存在: " + newName);
        }
        try {
            Files.move(target, newPath);
            log.info("重命名: workspaceId={}, {} → {}", workspaceId, relativePath, newName);
        } catch (IOException e) {
            throw new RuntimeException("重命名失败: " + relativePath, e);
        }
    }

    /**
     * 上传文件到工作空间指定目录。
     */
    public void uploadFile(String workspaceId, String relativeDirPath, MultipartFile file) {
        Path dirPath = resolvePath(workspaceId, relativeDirPath);
        if (!Files.isDirectory(dirPath)) {
            throw new IllegalArgumentException("目标路径不是目录: " + relativeDirPath);
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("文件名为空");
        }
        if (originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            throw new IllegalArgumentException("文件名包含非法字符");
        }
        Path destPath = dirPath.resolve(originalName);
        if (Files.exists(destPath)) {
            throw new IllegalArgumentException("文件已存在: " + originalName);
        }
        try {
            file.transferTo(destPath.toFile());
            log.info("上传文件: workspaceId={}, dir={}, file={}", workspaceId, relativeDirPath, originalName);
        } catch (IOException e) {
            throw new RuntimeException("上传文件失败: " + originalName, e);
        }
    }

    // ===================== 内部工具方法 =====================

    /**
     * 解析工作空间根目录。
     */
    Path resolveWsRoot(String workspaceId) {
        AgentWorkspace ws = workspaceMapper.selectById(workspaceId);
        if (ws == null) {
            throw new IllegalArgumentException("工作空间不存在: " + workspaceId);
        }
        return Paths.get(rootDir, ws.getDirName()).toAbsolutePath().normalize();
    }

    /**
     * 解析工作空间内的相对路径，含越界检查。
     */
    Path resolvePath(String workspaceId, String relativePath) {
        Path wsRoot = resolveWsRoot(workspaceId);
        Path target = wsRoot.resolve(relativePath).toAbsolutePath().normalize();
        if (!target.startsWith(wsRoot)) {
            throw new SecurityException("路径越界，无权访问！");
        }
        return target;
    }
}
