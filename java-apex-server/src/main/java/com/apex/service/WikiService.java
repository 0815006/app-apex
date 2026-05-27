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

    /**
     * 移动节点到指定父节点和排序位置。
     */
    @Transactional
    public void moveNode(String id, String newParentId, Integer newSortOrder) {
        WikiDocument doc = wikiDocumentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(404, "节点不存在");
        }
        // 防止循环引用：目标父节点不能是自己的子孙节点
        if (!"0".equals(newParentId)) {
            List<String> descendantIds = new ArrayList<>();
            collectChildrenIds(id, descendantIds);
            if (descendantIds.contains(newParentId) || newParentId.equals(id)) {
                throw new BusinessException("不能将节点移动到自身或自身的子孙节点下");
            }
            // 校验目标父节点存在且为文件夹
            WikiDocument targetParent = wikiDocumentMapper.selectById(newParentId);
            if (targetParent == null || targetParent.getType() != 1) {
                throw new BusinessException("目标父节点不存在或不是文件夹");
            }
        }
        // 如果父节点变了，需要调整原父节点下的同级排序
        if (!newParentId.equals(doc.getParentId())) {
            reorderSiblings(doc.getParentId());
        }
        // 更新 parentId 和 sortOrder
        doc.setParentId(newParentId);
        doc.setSortOrder(newSortOrder);
        wikiDocumentMapper.updateById(doc);
        // 重排目标父节点下的所有同级节点
        reorderSiblings(newParentId);
    }

    /**
     * 批量更新同级节点的 sort_order。
     */
    @Transactional
    public void batchUpdateSortOrder(List<com.apex.model.SortOrderDTO.SortItem> items) {
        for (com.apex.model.SortOrderDTO.SortItem item : items) {
            WikiDocument doc = wikiDocumentMapper.selectById(item.getId());
            if (doc != null) {
                doc.setSortOrder(item.getSortOrder());
                wikiDocumentMapper.updateById(doc);
            }
        }
    }

    /**
     * 重排指定父节点下所有子节点的 sort_order（使用整数间隔法：0, 10, 20, ...）。
     */
    private void reorderSiblings(String parentId) {
        List<WikiDocument> siblings = wikiDocumentMapper.selectList(
                new LambdaQueryWrapper<WikiDocument>()
                        .eq(WikiDocument::getParentId, parentId)
                        .orderByAsc(WikiDocument::getSortOrder)
        );
        for (int i = 0; i < siblings.size(); i++) {
            WikiDocument sib = siblings.get(i);
            sib.setSortOrder(i * 10);
            wikiDocumentMapper.updateById(sib);
        }
    }

    /**
     * 获取指定文件夹的直接子节点（不递归）。
     */
    public List<WikiNodeVO> getChildrenById(String folderId) {
        WikiDocument folder = wikiDocumentMapper.selectById(folderId);
        if (folder == null || folder.getType() != 1) {
            throw new BusinessException(404, "文件夹不存在");
        }
        List<WikiDocument> children = wikiDocumentMapper.selectList(
                new LambdaQueryWrapper<WikiDocument>()
                        .eq(WikiDocument::getParentId, folderId)
                        .orderByAsc(WikiDocument::getSortOrder)
        );
        return children.stream()
                .map(doc -> new WikiNodeVO(
                        doc.getId(),
                        doc.getTitle(),
                        doc.getType(),
                        doc.getParentId(),
                        doc.getUpdateTime()
                ))
                .toList();
    }
}
