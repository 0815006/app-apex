package com.apex.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 内置工具注册表（硬编码 6 大内置工具，不查数据库）。
 * 负责提供 OpenAI 兼容的 JSON Schema 并执行实际的工具调用。
 * 内置工具随 Java 源码打包迭代，零配置、零数据库依赖。
 */
@Slf4j
@Component
public class BuiltInToolRegistry {

    @Value("${apex.agent.workspace.root-dir}")
    private String workspaceRoot;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== 0. 内置工具枚举 ==========
    public enum Tool {
        LIST_DIR("list_dir", "递归列出工作空间内指定目录下的完整文件树（含所有子目录和文件）"),
        LOCATE_FILES("locate_files", "在工作空间中全局搜索文件或文本，支持关键词/正则"),
        READ_FILE("read_file", "读取当前工作空间内指定文件的文本内容"),
        WRITE_FILE("write_file", "在当前工作空间内新建文件或覆盖已有文件"),
        APPLY_DIFF("apply_diff", "精准替换文件中的局部代码片段，避免全量重写大文件"),
        EXECUTE_COMMAND("execute_command", "在工作空间路径下执行安全的系统命令，返回 stdout/stderr");

        private final String name;
        private final String description;

        Tool(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }

    /** 所有内置工具名称集合，用于执行分流判断 */
    public static final Set<String> BUILT_IN_TOOL_NAMES = Set.of(
            "list_dir", "locate_files", "read_file", "write_file", "apply_diff", "execute_command"
    );

    /** 会触发 file_changed SSE 事件的工具 */
    public static final Set<String> FILE_CHANGE_TOOLS = Set.of("write_file", "apply_diff");

    // ========== 1. 6 大内置工具 OpenAI Schema 定义 ==========
    public List<Map<String, Object>> getBuiltInToolsSchema() {
        return List.of(
                // list_dir
                Map.of("type", "function", "function", Map.of(
                        "name", Tool.LIST_DIR.getName(),
                        "description", Tool.LIST_DIR.getDescription(),
                        "parameters", Map.of("type", "object",
                                "properties", Map.of("relativePath", Map.of("type", "string",
                                        "description", "目录路径（相对），不传则递归列出整个子目录树（含所有文件和嵌套子目录）")),
                                "required", List.of())
                )),
                // locate_files
                Map.of("type", "function", "function", Map.of(
                        "name", Tool.LOCATE_FILES.getName(),
                        "description", Tool.LOCATE_FILES.getDescription(),
                        "parameters", Map.of("type", "object",
                                "properties", Map.of("query", Map.of("type", "string",
                                        "description", "搜索关键词或正则表达式，如 'max-retry' 或 'login'")),
                                "required", List.of("query"))
                )),
                // read_file
                Map.of("type", "function", "function", Map.of(
                        "name", Tool.READ_FILE.getName(),
                        "description", Tool.READ_FILE.getDescription(),
                        "parameters", Map.of("type", "object",
                                "properties", Map.of("relativePath", Map.of("type", "string",
                                        "description", "相对于工作空间根目录的相对路径，如 src/main.js")),
                                "required", List.of("relativePath"))
                )),
                // write_file
                Map.of("type", "function", "function", Map.of(
                        "name", Tool.WRITE_FILE.getName(),
                        "description", Tool.WRITE_FILE.getDescription(),
                        "parameters", Map.of("type", "object",
                                "properties", Map.of(
                                        "relativePath", Map.of("type", "string", "description",
                                                "⚠️ 必须使用用户明确指定的文件路径（含文件名），禁止自行编造、替换或猜测路径。例如用户说「在 docs 下创建 1.md」，则 relativePath 必须为 docs/1.md"),
                                        "content", Map.of("type", "string", "description", "要写入的完整文件内容")),
                                "required", List.of("relativePath", "content"))
                )),
                // apply_diff
                Map.of("type", "function", "function", Map.of(
                        "name", Tool.APPLY_DIFF.getName(),
                        "description", Tool.APPLY_DIFF.getDescription(),
                        "parameters", Map.of("type", "object",
                                "properties", Map.of(
                                        "relativePath", Map.of("type", "string", "description", "目标文件相对路径"),
                                        "searchContent", Map.of("type", "string", "description", "要替换的原代码片段（精确匹配）"),
                                        "replaceContent", Map.of("type", "string", "description", "替换后的新代码片段")),
                                "required", List.of("relativePath", "searchContent", "replaceContent"))
                )),
                // execute_command
                Map.of("type", "function", "function", Map.of(
                        "name", Tool.EXECUTE_COMMAND.getName(),
                        "description", Tool.EXECUTE_COMMAND.getDescription(),
                        "parameters", Map.of("type", "object",
                                "properties", Map.of("command", Map.of("type", "string",
                                        "description", "要执行的 Shell 命令，如 'mvn compile' 或 'go build ./...'")),
                                "required", List.of("command"))
                ))
        );
    }

    // ========== 2. 命令黑名单（高危指令拦截） ==========
    private static final List<Pattern> COMMAND_BLACKLIST = List.of(
            Pattern.compile("rm\\s+-rf\\s+/"),
            Pattern.compile("wget\\s+"),
            Pattern.compile("curl\\s+.*\\|\\s*(ba)?sh"),
            Pattern.compile("shutdown|reboot|halt"),
            Pattern.compile("passwd|chpasswd"),
            Pattern.compile(">\\s*/dev/")
    );

    // ========== 3. 统一的内置工具执行分发器（含安全沙箱） ==========
    public String executeBuiltInTool(String toolName, String argumentsJson, String dirName)
            throws Exception {
        Path wsRoot = Paths.get(workspaceRoot, dirName).toAbsolutePath().normalize();

        JsonNode args = objectMapper.readTree(argumentsJson);

        switch (toolName) {
            // ===== 空间感知 =====
            case "list_dir" -> {
                String relPath = args.has("relativePath") ? args.get("relativePath").asText() : "";
                Path targetPath = wsRoot.resolve(relPath).toAbsolutePath().normalize();
                if (!targetPath.startsWith(wsRoot))
                    throw new SecurityException("路径越界，无权访问！");
                StringBuilder sb = new StringBuilder();
                try (var stream = Files.walk(targetPath)) {
                    stream.sorted().forEach(p -> {
                        if (p.equals(targetPath)) return; // 跳过根自身
                        String prefix = Files.isDirectory(p) ? "[DIR]  " : "[FILE] ";
                        sb.append(prefix).append(wsRoot.relativize(p)).append("\n");
                    });
                }
                return sb.isEmpty() ? "（空目录）" : sb.toString();
            }
            case "locate_files" -> {
                String query = args.get("query").asText();
                StringBuilder sb = new StringBuilder();
                Pattern pattern = safeCompilePattern(query);
                try (var stream = Files.walk(wsRoot)) {
                    stream.filter(Files::isRegularFile)
                            .filter(p -> !p.getFileName().toString().startsWith("."))
                            .forEach(p -> {
                                try {
                                    String content = Files.readString(p);
                                    var matcher = pattern.matcher(content);
                                    while (matcher.find()) {
                                        int lineNum = content.substring(0, matcher.start()).split("\n", -1).length;
                                        sb.append(wsRoot.relativize(p)).append(":").append(lineNum)
                                                .append(" → ").append(matcher.group()).append("\n");
                                    }
                                } catch (IOException ignored) {}
                            });
                }
                return sb.isEmpty() ? "未找到匹配结果" : sb.toString();
            }
            // ===== 文件读写 =====
            case "read_file" -> {
                String relPath = args.get("relativePath").asText();
                Path targetPath = wsRoot.resolve(relPath).toAbsolutePath().normalize();
                if (!targetPath.startsWith(wsRoot))
                    throw new SecurityException("路径越界，无权访问！");
                if (Files.size(targetPath) > 2 * 1024 * 1024)
                    throw new IllegalArgumentException("文件超过 2MB 读取上限");
                return Files.readString(targetPath);
            }
            case "write_file" -> {
                String relPath = args.get("relativePath").asText();
                String content = args.get("content").asText();
                Path targetPath = wsRoot.resolve(relPath).toAbsolutePath().normalize();
                if (!targetPath.startsWith(wsRoot))
                    throw new SecurityException("路径越界，无权访问！");
                if (targetPath.getFileName().toString().startsWith("."))
                    throw new SecurityException("禁止操作隐藏文件");
                Files.createDirectories(targetPath.getParent());
                Files.writeString(targetPath, content);
                return "文件写入成功！路径: " + relPath;
            }
            // ===== 精准修改 =====
            case "apply_diff" -> {
                String relPath = args.get("relativePath").asText();
                String searchContent = args.get("searchContent").asText();
                String replaceContent = args.get("replaceContent").asText();
                Path targetPath = wsRoot.resolve(relPath).toAbsolutePath().normalize();
                if (!targetPath.startsWith(wsRoot))
                    throw new SecurityException("路径越界，无权访问！");
                if (targetPath.getFileName().toString().startsWith("."))
                    throw new SecurityException("禁止操作隐藏文件");
                // 统一换行符，防止大模型输出 \r\n 与文件中的 \n 不一致导致匹配失败
                String fileContent = Files.readString(targetPath).replace("\r\n", "\n");
                String cleanSearch = searchContent.replace("\r\n", "\n");
                String cleanReplace = replaceContent.replace("\r\n", "\n");
                if (!fileContent.contains(cleanSearch))
                    throw new IllegalArgumentException("未找到匹配的代码片段，请确认 searchContent 是否正确");
                String newContent = fileContent.replace(cleanSearch, cleanReplace);
                if (newContent.equals(fileContent))
                    throw new IllegalArgumentException("替换后内容未变化，可能 searchContent 存在歧义匹配");
                Files.writeString(targetPath, newContent);
                return "精准修改成功！路径: " + relPath;
            }
            // ===== 命令执行 =====
            case "execute_command" -> {
                String command = args.get("command").asText();
                for (Pattern p : COMMAND_BLACKLIST) {
                    if (p.matcher(command).find())
                        throw new SecurityException("命令被安全策略拦截: " + command);
                }
                ProcessBuilder pb = new ProcessBuilder();
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    // Windows cmd 不支持 -p 等 Unix 风格参数，做归一化处理
                    String normalized = normalizeWindowsCommand(command);
                    pb.command("cmd", "/c", normalized);
                } else {
                    pb.command("sh", "-c", command);
                }
                pb.directory(wsRoot.toFile());
                pb.redirectErrorStream(true);
                Process process = pb.start();
                boolean finished = process.waitFor(30, TimeUnit.SECONDS);
                String output;
                if (finished) {
                    output = new String(process.getInputStream().readAllBytes());
                } else {
                    output = new String(process.getInputStream().readNBytes(8192));
                    // 销毁所有子进程树，防止孤儿/僵尸进程
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                    output += "\n[系统] 命令执行超时（30s），已强制终止";
                }
                return output.isEmpty() ? "命令执行成功（无输出）" : output;
            }
            default -> throw new IllegalArgumentException("未知的内置工具: " + toolName);
        }
    }

    /**
     * 安全编译正则，若语法非法则退化为字面量匹配。
     */
    private Pattern safeCompilePattern(String regex) {
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            return Pattern.compile(Pattern.quote(regex), Pattern.CASE_INSENSITIVE);
        }
    }

    /**
     * Windows cmd.exe 下归一化大模型生成的 Unix 风格命令。
     * cmd 的 mkdir/md 默认递归创建父目录，无需 -p。
     */
    private String normalizeWindowsCommand(String command) {
        // mkdir -p X → mkdir X  (cmd 默认递归创建)
        command = command.replaceAll("(?i)mkdir\\s+-p\\s+", "mkdir ");
        // md -p X → md X
        command = command.replaceAll("(?i)md\\s+-p\\s+", "md ");
        return command;
    }
}
