package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.entity.EvalTag;
import com.bank.branch.platform.performance.eval.mapper.EvalTagMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 评价标签维护服务.
 * <p>提供评价标签的增删改查（带分页）能力，标签按名称唯一约束（已去类型化）。</p>
 */
@Slf4j
@Service
public class EvalTagService {

    private final EvalTagMapper evalTagMapper;

    @Autowired
    public EvalTagService(EvalTagMapper evalTagMapper) {
        this.evalTagMapper = evalTagMapper;
    }

    /**
     * 创建标签（无类型，按名称唯一）.
     *
     * @param tagName 标签名称
     * @return 创建后的标签实体
     * @throws PerfException 名称重复时抛 EVAL_TAG_NAME_DUP（PERF-40050）
     */
    @Transactional(rollbackFor = Exception.class)
    public EvalTag create(String tagName) {
        EvalTag existing = evalTagMapper.selectByName(tagName);
        if (existing != null) {
            throw new PerfException(PerfErrorCode.EVAL_TAG_NAME_DUP, tagName);
        }
        EvalTag tag = new EvalTag();
        tag.setTagName(tagName);
        tag.setStatus(1); // 默认启用
        evalTagMapper.insert(tag);
        log.info("[EvalTagService.create] 创建评价标签成功 tagName={} tagId={}", tagName, tag.getTagId());
        return tag;
    }

    /**
     * 更新标签名称和/或状态.
     *
     * @param tagId   标签ID
     * @param tagName 新名称（null 表示不更新）
     * @param status  新状态（null 表示不更新）
     * @throws PerfException 标签不存在时抛 EVAL_RULE_NOT_FOUND；改名后名称已存在时抛 EVAL_TAG_NAME_DUP
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long tagId, String tagName, Integer status) {
        EvalTag tag = evalTagMapper.selectById(tagId);
        if (tag == null) {
            throw new PerfException(PerfErrorCode.EVAL_RULE_NOT_FOUND, tagId);
        }
        // 改名时做唯一性校验，排除自身
        if (tagName != null && !tagName.equals(tag.getTagName())) {
            EvalTag dup = evalTagMapper.selectByName(tagName);
            if (dup != null && !dup.getTagId().equals(tagId)) {
                throw new PerfException(PerfErrorCode.EVAL_TAG_NAME_DUP, tagName);
            }
            tag.setTagName(tagName);
        }
        if (status != null) {
            tag.setStatus(status);
        }
        evalTagMapper.updateById(tag);
        log.info("[EvalTagService.update] 更新评价标签 tagId={} tagName={} status={}", tagId, tagName, status);
    }

    /**
     * 删除标签.
     *
     * @param tagId 标签ID
     * @throws PerfException 标签不存在时抛 EVAL_RULE_NOT_FOUND
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long tagId) {
        EvalTag tag = evalTagMapper.selectById(tagId);
        if (tag == null) {
            throw new PerfException(PerfErrorCode.EVAL_RULE_NOT_FOUND, tagId);
        }
        evalTagMapper.deleteById(tagId);
        log.info("[EvalTagService.delete] 删除评价标签 tagId={}", tagId);
    }

    /**
     * 查询全部标签（不分页，供下拉选项用）.
     *
     * @param status 状态筛选（可选，null 表示不过滤）
     * @return 标签列表
     */
    public List<EvalTag> listAll(Integer status) {
        return evalTagMapper.selectAll(status);
    }

    /**
     * 分页查询标签列表.
     *
     * @param keyword  标签名称关键词（可选，null 表示不过滤）
     * @param page     页码（从 1 开始）
     * @param pageSize 每页数量
     * @return 分页结果
     */
    public PageResult<EvalTag> list(String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<EvalTag> rows = evalTagMapper.selectByCondition(keyword, offset, pageSize);
        long total = evalTagMapper.countByCondition(keyword);
        return PageResult.of(page, pageSize, total, rows);
    }
}
