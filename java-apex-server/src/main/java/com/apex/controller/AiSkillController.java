package com.apex.controller;

import com.apex.common.Result;
import com.apex.entity.AiSkill;
import com.apex.model.SkillVO;
import com.apex.service.AiSkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI Skill 管理 Controller。
 * 仅提供 prompt 类型的 CRUD，后续可扩展 agent / workflow。
 */
@Slf4j
@RestController
@RequestMapping("/api/skill")
@RequiredArgsConstructor
public class AiSkillController {

    private final AiSkillService aiSkillService;

    /**
     * 获取所有启用的 Skill 列表（供 ChatView 中加号按钮选择）。
     */
    @GetMapping("/enabled")
    public Result<List<SkillVO>> listEnabled() {
        List<AiSkill> skills = aiSkillService.listAll();
        List<SkillVO> vos = skills.stream()
                .map(s -> new SkillVO(
                        s.getId(), s.getName(), s.getIcon(), s.getDescription(),
                        s.getType(), s.getSystemPrompt(), s.getTemperature(),
                        s.getWorkflowId(), s.getStatus(), s.getSortOrder(),
                        s.getCreateTime(), s.getUpdateTime()
                ))
                .toList();
        return Result.success(vos);
    }

    /**
     * 获取全部 Skill（管理页面用，含禁用）。
     */
    @GetMapping
    public Result<List<SkillVO>> listAll() {
        List<AiSkill> skills = aiSkillService.listAllWithDisabled();
        List<SkillVO> vos = skills.stream()
                .map(s -> new SkillVO(
                        s.getId(), s.getName(), s.getIcon(), s.getDescription(),
                        s.getType(), s.getSystemPrompt(), s.getTemperature(),
                        s.getWorkflowId(), s.getStatus(), s.getSortOrder(),
                        s.getCreateTime(), s.getUpdateTime()
                ))
                .toList();
        return Result.success(vos);
    }

    /**
     * 获取单个 Skill。
     */
    @GetMapping("/{id}")
    public Result<SkillVO> getById(@PathVariable String id) {
        AiSkill s = aiSkillService.getById(id);
        return Result.success(new SkillVO(
                s.getId(), s.getName(), s.getIcon(), s.getDescription(),
                s.getType(), s.getSystemPrompt(), s.getTemperature(),
                s.getWorkflowId(), s.getStatus(), s.getSortOrder(),
                s.getCreateTime(), s.getUpdateTime()
        ));
    }

    /**
     * 新增 Skill。
     */
    @PostMapping
    public Result<SkillVO> create(@RequestBody AiSkill skill) {
        AiSkill created = aiSkillService.create(skill);
        return Result.success(new SkillVO(
                created.getId(), created.getName(), created.getIcon(), created.getDescription(),
                created.getType(), created.getSystemPrompt(), created.getTemperature(),
                created.getWorkflowId(), created.getStatus(), created.getSortOrder(),
                created.getCreateTime(), created.getUpdateTime()
        ));
    }

    /**
     * 更新 Skill。
     */
    @PutMapping("/{id}")
    public Result<SkillVO> update(@PathVariable String id, @RequestBody AiSkill skill) {
        skill.setId(id);
        AiSkill updated = aiSkillService.update(skill);
        return Result.success(new SkillVO(
                updated.getId(), updated.getName(), updated.getIcon(), updated.getDescription(),
                updated.getType(), updated.getSystemPrompt(), updated.getTemperature(),
                updated.getWorkflowId(), updated.getStatus(), updated.getSortOrder(),
                updated.getCreateTime(), updated.getUpdateTime()
        ));
    }

    /**
     * 删除 Skill。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        aiSkillService.delete(id);
        return Result.success();
    }
}
