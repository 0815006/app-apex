package com.apex.service;

import com.apex.common.BusinessException;
import com.apex.common.EmpContext;
import com.apex.entity.LlmConfig;
import com.apex.mapper.LlmConfigMapper;
import com.apex.model.LlmConfigVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * LLM 配置管理 Service。
 * 所有操作基于当前登录用户（EmpContext.getEmpNo()）做多租户隔离。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {

    private final LlmConfigMapper llmConfigMapper;

    /**
     * 获取当前用户的所有 LLM 配置（下拉框用，不暴露 apiKey）。
     */
    public List<LlmConfigVO> listConfigs() {
        String empNo = EmpContext.getEmpNo();
        List<LlmConfig> configs = llmConfigMapper.selectList(
                new LambdaQueryWrapper<LlmConfig>()
                        .eq(LlmConfig::getUserId, empNo)
                        .orderByDesc(LlmConfig::getUpdateTime)
        );
        return configs.stream()
                .map(c -> new LlmConfigVO(c.getId(), c.getConfigName(), c.getModelName()))
                .toList();
    }

    /**
     * 根据 ID 获取完整配置（含 apiKey，用于后端代理转发）。
     * 必须校验归属于当前用户。
     */
    public LlmConfig getByIdForCurrentUser(String id) {
        String empNo = EmpContext.getEmpNo();
        LlmConfig config = llmConfigMapper.selectOne(
                new LambdaQueryWrapper<LlmConfig>()
                        .eq(LlmConfig::getId, id)
                        .eq(LlmConfig::getUserId, empNo)
        );
        if (config == null) {
            throw new BusinessException(404, "LLM 配置不存在或无权访问");
        }
        return config;
    }

    /**
     * 新增 LLM 配置。
     */
    @Transactional
    public LlmConfig create(LlmConfig config) {
        config.setUserId(EmpContext.getEmpNo());
        config.setId(null); // 让 MyBatis-Plus 自动生成雪花 ID
        llmConfigMapper.insert(config);
        log.info("[{}] 新增 LLM 配置: {}", EmpContext.getEmpNo(), config.getConfigName());
        return config;
    }

    /**
     * 更新 LLM 配置（仅允许修改自己的配置）。
     */
    @Transactional
    public LlmConfig update(LlmConfig config) {
        String empNo = EmpContext.getEmpNo();
        LlmConfig exist = llmConfigMapper.selectOne(
                new LambdaQueryWrapper<LlmConfig>()
                        .eq(LlmConfig::getId, config.getId())
                        .eq(LlmConfig::getUserId, empNo)
        );
        if (exist == null) {
            throw new BusinessException(404, "LLM 配置不存在或无权修改");
        }
        config.setUserId(empNo); // 防止越权修改 userId
        llmConfigMapper.updateById(config);
        log.info("[{}] 更新 LLM 配置: {}", empNo, config.getConfigName());
        return llmConfigMapper.selectById(config.getId());
    }

    /**
     * 删除 LLM 配置（仅允许删除自己的配置）。
     */
    @Transactional
    public void delete(String id) {
        String empNo = EmpContext.getEmpNo();
        LlmConfig exist = llmConfigMapper.selectOne(
                new LambdaQueryWrapper<LlmConfig>()
                        .eq(LlmConfig::getId, id)
                        .eq(LlmConfig::getUserId, empNo)
        );
        if (exist == null) {
            throw new BusinessException(404, "LLM 配置不存在或无权删除");
        }
        llmConfigMapper.deleteById(id);
        log.info("[{}] 删除 LLM 配置: {}", empNo, exist.getConfigName());
    }
}
