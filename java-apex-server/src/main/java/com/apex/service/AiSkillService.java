package com.apex.service;

import com.apex.common.BusinessException;
import com.apex.entity.AiSkill;
import com.apex.mapper.AiSkillMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI Skill 管理 Service。
 * 现阶段仅支持 prompt 类型的 CRUD。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSkillService {

    private final AiSkillMapper aiSkillMapper;

    /**
     * 获取所有启用的 Skill 列表（按 sort_order 升序）。
     */
    public List<AiSkill> listAll() {
        return aiSkillMapper.selectList(
                new LambdaQueryWrapper<AiSkill>()
                        .eq(AiSkill::getStatus, 1)
                        .orderByAsc(AiSkill::getSortOrder)
        );
    }

    /**
     * 获取全部 Skill（含禁用的，管理页面用）。
     */
    public List<AiSkill> listAllWithDisabled() {
        return aiSkillMapper.selectList(
                new LambdaQueryWrapper<AiSkill>()
                        .orderByAsc(AiSkill::getSortOrder)
        );
    }

    /**
     * 根据 ID 获取 Skill。
     */
    public AiSkill getById(String id) {
        AiSkill skill = aiSkillMapper.selectById(id);
        if (skill == null) {
            throw new BusinessException(404, "Skill 不存在");
        }
        return skill;
    }

    /**
     * 新增 Skill。
     */
    @Transactional
    public AiSkill create(AiSkill skill) {
        skill.setId(null);
        if (skill.getType() == null || skill.getType().isBlank()) {
            skill.setType("prompt");
        }
        if (skill.getStatus() == null) {
            skill.setStatus(1);
        }
        if (skill.getSortOrder() == null) {
            skill.setSortOrder(0);
        }
        aiSkillMapper.insert(skill);
        log.info("新增 Skill: id={}, name={}, type={}", skill.getId(), skill.getName(), skill.getType());
        return skill;
    }

    /**
     * 更新 Skill。
     */
    @Transactional
    public AiSkill update(AiSkill skill) {
        AiSkill exist = aiSkillMapper.selectById(skill.getId());
        if (exist == null) {
            throw new BusinessException(404, "Skill 不存在");
        }
        aiSkillMapper.updateById(skill);
        log.info("更新 Skill: id={}, name={}", skill.getId(), skill.getName());
        return aiSkillMapper.selectById(skill.getId());
    }

    /**
     * 删除 Skill。
     */
    @Transactional
    public void delete(String id) {
        AiSkill exist = aiSkillMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(404, "Skill 不存在");
        }
        aiSkillMapper.deleteById(id);
        log.info("删除 Skill: id={}, name={}", id, exist.getName());
    }
}
