package com.apex.service;

import com.apex.entity.AgentWorkspace;
import com.apex.mapper.AgentWorkspaceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 工作空间路径解析器。
 * 将 workspaceId 映射为磁盘上的物理路径：rootDir + dirName
 */
@Component
@RequiredArgsConstructor
public class WorkspaceResolver {

    @Value("${apex.agent.workspace.root-dir}")
    private String rootDir;

    private final AgentWorkspaceMapper workspaceMapper;

    /**
     * 根据 workspaceId 解析磁盘绝对路径。
     */
    public String resolve(String workspaceId) {
        AgentWorkspace ws = workspaceMapper.selectById(workspaceId);
        if (ws == null) {
            throw new IllegalArgumentException("工作空间不存在: " + workspaceId);
        }
        return rootDir + File.separator + ws.getDirName();
    }

    /**
     * 根据 workspaceId 获取 dirName（用于 BuiltInToolRegistry 执行）。
     */
    public String getDirName(String workspaceId) {
        AgentWorkspace ws = workspaceMapper.selectById(workspaceId);
        if (ws == null) {
            throw new IllegalArgumentException("工作空间不存在: " + workspaceId);
        }
        return ws.getDirName();
    }
}
