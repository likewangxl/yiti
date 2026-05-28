package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.entity.EvalRule;
import com.bank.branch.platform.performance.eval.entity.EvalRuleGroup;
import com.bank.branch.platform.performance.eval.mapper.EvalRuleGroupMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalRuleMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 评价关系规则维护服务.
 * <p>提供规则的增删改查能力，每条规则关联若干评价人组（EVAL_RULE_GROUP），
 * 要求所有组的权重之和等于 100.00%。</p>
 */
@Slf4j
@Service
public class EvalRuleService {

    private final EvalRuleMapper evalRuleMapper;
    private final EvalRuleGroupMapper evalRuleGroupMapper;

    @Autowired
    public EvalRuleService(EvalRuleMapper evalRuleMapper, EvalRuleGroupMapper evalRuleGroupMapper) {
        this.evalRuleMapper = evalRuleMapper;
        this.evalRuleGroupMapper = evalRuleGroupMapper;
    }

    // =============================================
    // 内部参数封装
    // =============================================

    /**
     * 评价人组参数，用于 create/update 时传入.
     */
    @Data
    @AllArgsConstructor
    public static class GroupParam {
        /** 组类型：1=按标签选人, 2=部门员工组. */
        private Integer groupType;
        /** type=1 时的评价人标签ID（type=2 时传 null）. */
        private Long evalTagId;
        /** 权重百分比（如 60.00），所有组之和必须 = 100.00. */
        private BigDecimal weight;
        /** 排序序号. */
        private Integer sortOrder;
        /** 评分方式：1=数值打分（默认）, 2=等级打分. */
        private Integer scoreMode;
    }

    // =============================================
    // 写操作
    // =============================================

    /**
     * 创建评价规则.
     *
     * <p>校验顺序：① 权重之和必须等于 100（前置，避免无效 DB 写入）；
     * ② beEvalTagId 唯一性校验；③ 插入主记录；④ 批量插入评价人组。</p>
     *
     * @param ruleName    规则名称
     * @param beEvalTagId 被评价人标签ID（唯一，一个标签只能有一条规则）
     * @param groups      评价人组参数列表
     * @return 创建后的规则实体
     * @throws PerfException 权重之和 ≠ 100% 时抛 EVAL_RULE_WEIGHT_INVALID（PERF-40052）
     * @throws PerfException beEvalTagId 已存在规则时抛 EVAL_RULE_TAG_EXISTS（PERF-40051）
     */
    @Transactional(rollbackFor = Exception.class)
    public EvalRule create(String ruleName, Long beEvalTagId, List<GroupParam> groups) {
        // 权重校验优先，避免到 DB 操作才发现错误
        validateWeightSum(groups);

        // 被评价人标签唯一性校验
        EvalRule existing = evalRuleMapper.selectByBeEvalTagId(beEvalTagId);
        if (existing != null) {
            throw new PerfException(PerfErrorCode.EVAL_RULE_TAG_EXISTS, beEvalTagId);
        }

        // 插入主记录
        EvalRule rule = new EvalRule();
        rule.setRuleName(ruleName);
        rule.setBeEvalTagId(beEvalTagId);
        rule.setStatus(1); // 默认启用
        evalRuleMapper.insert(rule);

        // 批量插入评价人组
        List<EvalRuleGroup> groupEntities = buildGroupEntities(rule.getRuleId(), groups);
        evalRuleGroupMapper.batchInsert(groupEntities);

        log.info("[EvalRuleService.create] 创建评价规则成功 ruleName={} beEvalTagId={} ruleId={}",
                ruleName, beEvalTagId, rule.getRuleId());
        return rule;
    }

    /**
     * 更新评价规则（名称 + 评价人组）.
     *
     * <p>采用先删后建策略重建评价人组。</p>
     *
     * @param ruleId   规则ID
     * @param ruleName 新规则名称
     * @param groups   新评价人组参数列表
     * @throws PerfException 规则不存在时抛 EVAL_RULE_NOT_FOUND（PERF-40058）
     * @throws PerfException 权重之和 ≠ 100% 时抛 EVAL_RULE_WEIGHT_INVALID（PERF-40052）
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long ruleId, String ruleName, List<GroupParam> groups) {
        EvalRule rule = evalRuleMapper.selectById(ruleId);
        if (rule == null) {
            throw new PerfException(PerfErrorCode.EVAL_RULE_NOT_FOUND, ruleId);
        }

        // 权重校验
        validateWeightSum(groups);

        // 更新主记录
        rule.setRuleName(ruleName);
        evalRuleMapper.updateById(rule);

        // 先删后建评价人组
        evalRuleGroupMapper.deleteByRuleId(ruleId);
        List<EvalRuleGroup> groupEntities = buildGroupEntities(ruleId, groups);
        evalRuleGroupMapper.batchInsert(groupEntities);

        log.info("[EvalRuleService.update] 更新评价规则 ruleId={} ruleName={}", ruleId, ruleName);
    }

    /**
     * 删除评价规则.
     *
     * <p>先删子表评价人组，再删主记录。</p>
     *
     * @param ruleId 规则ID
     * @throws PerfException 规则不存在时抛 EVAL_RULE_NOT_FOUND（PERF-40058）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long ruleId) {
        EvalRule rule = evalRuleMapper.selectById(ruleId);
        if (rule == null) {
            throw new PerfException(PerfErrorCode.EVAL_RULE_NOT_FOUND, ruleId);
        }
        // 先删子表，再删主记录，保持参照完整性
        evalRuleGroupMapper.deleteByRuleId(ruleId);
        evalRuleMapper.deleteById(ruleId);
        log.info("[EvalRuleService.delete] 删除评价规则 ruleId={}", ruleId);
    }

    // =============================================
    // 读操作
    // =============================================

    /**
     * 根据 ID 查询规则.
     *
     * @param ruleId 规则ID
     * @return 规则实体，不存在时返回 null
     */
    public EvalRule getById(Long ruleId) {
        return evalRuleMapper.selectById(ruleId);
    }

    /**
     * 查询规则下所有评价人组.
     *
     * @param ruleId 规则ID
     * @return 评价人组列表（按 sort_order 排序）
     */
    public List<EvalRuleGroup> getGroupsByRuleId(Long ruleId) {
        return evalRuleGroupMapper.selectByRuleId(ruleId);
    }

    /**
     * 根据被评价人标签ID查询规则.
     *
     * @param beEvalTagId 被评价人标签ID
     * @return 规则实体，不存在时返回 null
     */
    public EvalRule getByBeEvalTagId(Long beEvalTagId) {
        return evalRuleMapper.selectByBeEvalTagId(beEvalTagId);
    }

    /**
     * 分页查询规则列表.
     *
     * @param keyword  规则名称关键词（可选，null 表示不过滤）
     * @param page     页码（从 1 开始）
     * @param pageSize 每页数量
     * @return 分页结果
     */
    public PageResult<EvalRule> list(String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<EvalRule> rows = evalRuleMapper.selectByCondition(keyword, offset, pageSize);
        long total = evalRuleMapper.countByCondition(keyword);
        return PageResult.of(page, pageSize, total, rows);
    }

    // =============================================
    // 私有工具方法
    // =============================================

    /**
     * 校验评价人组权重之和是否等于 100.00.
     *
     * <p>必须在任何 DB 操作之前调用，权重不合法时提前终止，不触碰 mapper。</p>
     *
     * @param groups 评价人组参数列表
     * @throws PerfException 权重之和 ≠ 100.00 时抛 EVAL_RULE_WEIGHT_INVALID
     */
    private void validateWeightSum(List<GroupParam> groups) {
        BigDecimal sum = groups.stream()
                .map(GroupParam::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(new BigDecimal("100.00")) != 0) {
            throw new PerfException(PerfErrorCode.EVAL_RULE_WEIGHT_INVALID, sum);
        }
    }

    /**
     * 将 GroupParam 列表转换为 EvalRuleGroup 实体列表.
     *
     * @param ruleId 所属规则ID
     * @param groups 参数列表
     * @return 实体列表
     */
    private List<EvalRuleGroup> buildGroupEntities(Long ruleId, List<GroupParam> groups) {
        return groups.stream().map(p -> {
            EvalRuleGroup g = new EvalRuleGroup();
            g.setRuleId(ruleId);
            g.setGroupType(p.getGroupType());
            g.setEvalTagId(p.getEvalTagId());
            g.setWeight(p.getWeight());
            g.setSortOrder(p.getSortOrder());
            g.setScoreMode(p.getScoreMode() != null ? p.getScoreMode() : 1);
            return g;
        }).collect(Collectors.toList());
    }
}
