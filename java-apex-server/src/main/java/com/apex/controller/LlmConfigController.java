package com.apex.controller;

import com.apex.common.EmpContext;
import com.apex.common.Result;
import com.apex.entity.LlmConfig;
import com.apex.model.LlmConfigVO;
import com.apex.service.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * LLM 配置 Controller。
 */
@Slf4j
@RestController
@RequestMapping("/api/llm-config")
@RequiredArgsConstructor
public class LlmConfigController {

    private final LlmService llmService;

    /**
     * 获取当前用户的所有 LLM 配置（下拉框用）。
     */
    @GetMapping
    public Result<List<LlmConfigVO>> list() {
        log.info("[{}] 查询 LLM 配置列表", EmpContext.getEmpNo());
        return Result.success(llmService.listConfigs());
    }

    /**
     * 获取单个配置详情（含 apiKey，管理员用）。
     */
    @GetMapping("/{id}")
    public Result<LlmConfig> getById(@PathVariable String id) {
        log.info("[{}] 查询 LLM 配置详情: id={}", EmpContext.getEmpNo(), id);
        return Result.success(llmService.getByIdForCurrentUser(id));
    }

    /**
     * 新增 LLM 配置。
     */
    @PostMapping
    public Result<LlmConfig> create(@RequestBody LlmConfig config) {
        log.info("[{}] 新增 LLM 配置: name={}", EmpContext.getEmpNo(), config.getConfigName());
        return Result.success(llmService.create(config));
    }

    /**
     * 更新 LLM 配置。
     */
    @PutMapping("/{id}")
    public Result<LlmConfig> update(@PathVariable String id, @RequestBody LlmConfig config) {
        config.setId(id);
        log.info("[{}] 更新 LLM 配置: id={}", EmpContext.getEmpNo(), id);
        return Result.success(llmService.update(config));
    }

    /**
     * 删除 LLM 配置。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        log.info("[{}] 删除 LLM 配置: id={}", EmpContext.getEmpNo(), id);
        llmService.delete(id);
        return Result.success();
    }
}
