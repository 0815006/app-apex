package com.apex.controller;

import com.apex.common.EmpContext;
import com.apex.common.Result;
import com.apex.entity.WikiDocument;
import com.apex.model.WikiNodeVO;
import com.apex.service.WikiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Wiki 管理 Controller。
 */
@Slf4j
@RestController
@RequestMapping("/api/wiki")
@RequiredArgsConstructor
public class WikiController {

    private final WikiService wikiService;

    /**
     * 获取完整目录树。
     */
    @GetMapping("/tree")
    public Result<List<WikiNodeVO>> getTree() {
        log.info("[{}] 请求 Wiki 目录树", EmpContext.getEmpNo());
        return Result.success(wikiService.buildWikiTree());
    }

    /**
     * 根据 ID 获取文档详情。
     */
    @GetMapping("/{id}")
    public Result<WikiDocument> getById(@PathVariable String id) {
        log.info("[{}] 请求 Wiki 文档详情: id={}", EmpContext.getEmpNo(), id);
        return Result.success(wikiService.getById(id));
    }

    /**
     * 根据标题获取文档（双链跳转专用）。
     */
    @GetMapping("/by-title")
    public Result<WikiDocument> getByTitle(@RequestParam String title) {
        log.info("[{}] 双链跳转查询: title={}", EmpContext.getEmpNo(), title);
        return Result.success(wikiService.getByTitle(title));
    }

    /**
     * 创建或更新文档。
     */
    @PostMapping("/save")
    public Result<WikiDocument> save(@RequestBody WikiDocument doc) {
        log.info("[{}] 保存 Wiki 文档: title={}, id={}", EmpContext.getEmpNo(), doc.getTitle(), doc.getId());
        return Result.success(wikiService.saveOrUpdate(doc));
    }

    /**
     * 删除文档及子节点。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        log.info("[{}] 删除 Wiki 节点: id={}", EmpContext.getEmpNo(), id);
        wikiService.deleteById(id);
        return Result.success();
    }
}
