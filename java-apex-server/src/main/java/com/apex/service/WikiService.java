package com.apex.service;

import com.apex.common.BusinessException;
import com.apex.entity.WikiDocument;
import com.apex.mapper.WikiDocumentMapper;
import com.apex.model.WikiNodeVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Wiki 核心业务 Service。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiService {

    private final WikiDocumentMapper wikiDocumentMapper;

    /**
     * 获取完整树形目录。
     */
    public List<WikiNodeVO> buildWikiTree() {
        List<WikiDocument> allDocs = wikiDocumentMapper.selectList(
                new LambdaQueryWrapper<WikiDocument>().orderByAsc(WikiDocument::getSortOrder)
        );
        return allDocs.stream()
                .filter(doc -> "0".equals(doc.getParentId()))
                .map(doc -> convertToVO(doc, allDocs))
                .toList();
    }

    /**
     * 递归转换平铺记录为树形 VO。
     */
    private WikiNodeVO convertToVO(WikiDocument doc, List<WikiDocument> allDocs) {
        List<WikiNodeVO> children = allDocs.stream()
                .filter(child -> doc.getId().equals(child.getParentId()))
                .map(child -> convertToVO(child, allDocs))
                .toList();
        return new WikiNodeVO(
                doc.getId(),
                doc.getTitle(),
                doc.getType(),
                doc.getParentId(),
                doc.getUpdateTime(),
                children.isEmpty() ? null : children
        );
    }

    /**
     * 根据 ID 获取文档详情。
     */
    public WikiDocument getById(String id) {
        WikiDocument doc = wikiDocumentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }
        return doc;
    }

    /**
     * 根据标题获取文档（用于双链跳转）。
     */
    public WikiDocument getByTitle(String title) {
        WikiDocument doc = wikiDocumentMapper.selectOne(
                new LambdaQueryWrapper<WikiDocument>().eq(WikiDocument::getTitle, title)
        );
        if (doc == null) {
            throw new BusinessException(404, "未找到标题为「" + title + "」的文档");
        }
        return doc;
    }

    /**
     * 保存或更新文档。
     * 若 entity.id 为 null，MyBatis-Plus 自动生成雪花 String ID。
     */
    @Transactional
    public WikiDocument saveOrUpdate(WikiDocument doc) {
        // 标题唯一性校验
        LambdaQueryWrapper<WikiDocument> wrapper = new LambdaQueryWrapper<WikiDocument>()
                .eq(WikiDocument::getTitle, doc.getTitle());
        WikiDocument existByTitle = wikiDocumentMapper.selectOne(wrapper);
        if (existByTitle != null && !existByTitle.getId().equals(doc.getId())) {
            throw new BusinessException("标题「" + doc.getTitle() + "」已存在，请更换标题");
        }

        wikiDocumentMapper.insertOrUpdate(doc);
        // 重新查询返回完整数据（包含自动生成的 id 和时间戳）
        return wikiDocumentMapper.selectById(doc.getId());
    }

    /**
     * 删除文档（若为文件夹则级联删除所有子节点）。
     */
    @Transactional
    public void deleteById(String id) {
        WikiDocument doc = wikiDocumentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }
        // 收集所有需要删除的子节点 ID
        List<String> idsToDelete = new ArrayList<>();
        idsToDelete.add(id);
        collectChildrenIds(id, idsToDelete);
        wikiDocumentMapper.deleteBatchIds(idsToDelete);
        log.info("删除 Wiki 节点 {} 及其子节点共 {} 个", id, idsToDelete.size());
    }

    /**
     * 递归收集所有子孙节点 ID。
     */
    private void collectChildrenIds(String parentId, List<String> collector) {
        List<WikiDocument> children = wikiDocumentMapper.selectList(
                new LambdaQueryWrapper<WikiDocument>().eq(WikiDocument::getParentId, parentId)
        );
        for (WikiDocument child : children) {
            collector.add(child.getId());
            collectChildrenIds(child.getId(), collector);
        }
    }
}
