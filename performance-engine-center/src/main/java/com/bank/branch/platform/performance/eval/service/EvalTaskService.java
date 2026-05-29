package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.entity.EvalRuleGroup;
import com.bank.branch.platform.performance.eval.entity.EvalScore;
import com.bank.branch.platform.performance.eval.entity.EvalTask;
import com.bank.branch.platform.performance.eval.entity.EvalTaskTarget;
import com.bank.branch.platform.performance.eval.mapper.EvalRuleGroupMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalRuleMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalScoreMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalTaskMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalTaskTargetMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalUserTagMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评价任务管理与得分计算服务.
 *
 * <p>职责：
 * <ul>
 *   <li>创建评价任务：校验截止时间、为每个被评价人匹配规则、生成 target 记录</li>
 *   <li>关闭任务：更新状态并触发得分计算</li>
 *   <li>计算得分：按规则组加权平均，写入 EVAL_TASK_TARGET.final_score</li>
 *   <li>分页查询任务列表及明细</li>
 * </ul>
 */
@Slf4j
@Service
public class EvalTaskService {

    private final EvalTaskMapper evalTaskMapper;
    private final EvalTaskTargetMapper evalTaskTargetMapper;
    private final EvalRuleMapper evalRuleMapper;
    private final EvalRuleGroupMapper evalRuleGroupMapper;
    private final EvalScoreMapper evalScoreMapper;
    private final EvalUserTagMapper evalUserTagMapper;

    @Autowired
    public EvalTaskService(EvalTaskMapper evalTaskMapper,
                           EvalTaskTargetMapper evalTaskTargetMapper,
                           EvalRuleMapper evalRuleMapper,
                           EvalRuleGroupMapper evalRuleGroupMapper,
                           EvalScoreMapper evalScoreMapper,
                           EvalUserTagMapper evalUserTagMapper) {
        this.evalTaskMapper = evalTaskMapper;
        this.evalTaskTargetMapper = evalTaskTargetMapper;
        this.evalRuleMapper = evalRuleMapper;
        this.evalRuleGroupMapper = evalRuleGroupMapper;
        this.evalScoreMapper = evalScoreMapper;
        this.evalUserTagMapper = evalUserTagMapper;
    }

    /**
     * 创建评价任务.
     *
     * <p>步骤：
     * <ol>
     *   <li>校验 endTime > now，否则抛 EVAL_TASK_END_TIME_INVALID</li>
     *   <li>持久化 EvalTask 记录（status=0 进行中）</li>
     *   <li>对每个被评价人查询其被评价人标签（role_type=1），匹配对应 EvalRule，生成 EvalTaskTarget</li>
     * </ol>
     *
     * @param taskName       任务名称
     * @param endTime        截止时间（必须晚于当前时间）
     * @param beEvalUserIds  被评价人 USER_ID 列表
     * @param createBy       创建人 USER_ID
     * @return 创建后的任务实体
     * @throws PerfException endTime <= now 时抛 EVAL_TASK_END_TIME_INVALID
     */
    @Transactional(rollbackFor = Exception.class)
    public EvalTask createTask(String taskName, LocalDateTime endTime,
                               List<String> beEvalUserIds, String createBy) {
        // 1. 截止时间校验
        if (endTime == null || !endTime.isAfter(LocalDateTime.now())) {
            throw new PerfException(PerfErrorCode.EVAL_TASK_END_TIME_INVALID, endTime);
        }

        // 2. 持久化任务
        EvalTask task = new EvalTask();
        task.setTaskName(taskName);
        task.setStartTime(LocalDateTime.now());
        task.setEndTime(endTime);
        task.setStatus(0); // 进行中
        task.setCreateBy(createBy);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        evalTaskMapper.insert(task);

        // 3. 为每个被评价人生成 target（匹配规则）
        List<EvalTaskTarget> targets = new ArrayList<>();
        for (String userId : beEvalUserIds) {
            // 查询被评价人的被评价人标签（role_type=1）
            List<Long> beEvalTagIds = evalUserTagMapper.selectTagIdsByUserIdAndType(userId, 1);
            Long ruleId = null;
            for (Long tagId : beEvalTagIds) {
                // 按被评价人标签查询匹配规则
                var rule = evalRuleMapper.selectByBeEvalTagId(tagId);
                if (rule != null && rule.getStatus() == 1) {
                    ruleId = rule.getRuleId();
                    break;
                }
            }
            // 未匹配到规则时跳过（不强制，允许部分被评价人无规则）
            if (ruleId == null) {
                log.warn("[EvalTaskService.createTask] 被评价人 userId={} 无匹配启用规则，跳过生成 target", userId);
                continue;
            }
            EvalTaskTarget target = new EvalTaskTarget();
            target.setTaskId(task.getTaskId());
            target.setBeEvalUserId(userId);
            target.setRuleId(ruleId);
            targets.add(target);
        }
        if (!targets.isEmpty()) {
            evalTaskTargetMapper.batchInsert(targets);
        }

        log.info("[EvalTaskService.createTask] 创建评价任务成功 taskId={} taskName={} targetCount={}",
                task.getTaskId(), taskName, targets.size());
        return task;
    }

    /**
     * 关闭任务并触发得分计算.
     *
     * @param taskId 任务ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void closeTask(Long taskId) {
        evalTaskMapper.closeTask(taskId);
        calculateScoresForTask(taskId);
        log.info("[EvalTaskService.closeTask] 任务已关闭并计算得分 taskId={}", taskId);
    }

    /**
     * 计算指定任务内所有被评价人的最终得分.
     *
     * <p>得分公式（按规则组加权平均）：
     * <pre>
     * 对每个 target：
     *   groups = evalRuleGroupMapper.selectByRuleId(target.ruleId)
     *   allScores = evalScoreMapper.selectByTargetId(target.targetId)，按 groupId 分组
     *   对每个 group：
     *     该组 scores 为空 → 贡献 0（跳过）
     *     avg = sum(score) / count，scale=1，HALF_UP
     *     groupScore = avg * weight / 100，scale=1，HALF_UP
     *     totalScore += groupScore
     *   updateFinalScore(targetId, totalScore)
     * </pre>
     *
     * @param taskId 任务ID
     */
    public void calculateScoresForTask(Long taskId) {
        List<EvalTaskTarget> targets = evalTaskTargetMapper.selectByTaskId(taskId);
        for (EvalTaskTarget target : targets) {
            BigDecimal finalScore = computeFinalScore(target);
            evalTaskTargetMapper.updateFinalScore(target.getTargetId(), finalScore);
            log.info("[EvalTaskService.calculateScoresForTask] targetId={} finalScore={}",
                    target.getTargetId(), finalScore);
        }
    }

    /**
     * 分页查询任务列表.
     *
     * @param status   状态筛选（null=不过滤，0=进行中，1=已结束）
     * @param keyword  任务名称关键词（可选）
     * @param page     页码（从 1 开始）
     * @param pageSize 每页数量
     * @return 分页结果
     */
    public PageResult<EvalTask> list(Integer status, String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<EvalTask> rows = evalTaskMapper.selectByCondition(status, keyword, offset, pageSize);
        long total = evalTaskMapper.countByCondition(status, keyword);
        return PageResult.of(page, pageSize, total, rows);
    }

    /**
     * 按 ID 查询任务.
     *
     * @param taskId 任务ID
     * @return 任务实体，不存在则返回 null
     */
    public EvalTask getById(Long taskId) {
        return evalTaskMapper.selectById(taskId);
    }

    /**
     * 查询任务下的所有被评价人明细.
     *
     * @param taskId 任务ID
     * @return 被评价人明细列表
     */
    public List<EvalTaskTarget> getTargetsByTaskId(Long taskId) {
        return evalTaskTargetMapper.selectByTaskId(taskId);
    }

    // ──────────────────────────────────────────────────────────
    // 私有方法
    // ──────────────────────────────────────────────────────────

    /**
     * 计算单个 target 的最终得分（按规则组加权平均）.
     *
     * @param target 被评价人明细
     * @return 最终得分（scale=1，HALF_UP），无有效组得分时返回 0.0
     */
    private BigDecimal computeFinalScore(EvalTaskTarget target) {
        List<EvalRuleGroup> groups = evalRuleGroupMapper.selectByRuleId(target.getRuleId());
        List<EvalScore> allScores = evalScoreMapper.selectByTargetId(target.getTargetId());

        // 按 groupId 聚合打分
        Map<Long, List<EvalScore>> scoresByGroup = allScores.stream()
                .collect(Collectors.groupingBy(EvalScore::getGroupId));

        BigDecimal totalScore = BigDecimal.ZERO;
        for (EvalRuleGroup group : groups) {
            List<EvalScore> groupScores = scoresByGroup.get(group.getGroupId());
            if (groupScores == null || groupScores.isEmpty()) {
                // 该组无人打分，贡献 0
                continue;
            }
            // avg = sum / count，scale=1，HALF_UP
            int sum = groupScores.stream().mapToInt(EvalScore::getScore).sum();
            BigDecimal avg = BigDecimal.valueOf(sum)
                    .divide(BigDecimal.valueOf(groupScores.size()), 1, RoundingMode.HALF_UP);
            // groupScore = avg * weight / 100，scale=1，HALF_UP
            BigDecimal groupScore = avg.multiply(group.getWeight())
                    .divide(BigDecimal.valueOf(100), 1, RoundingMode.HALF_UP);
            totalScore = totalScore.add(groupScore);
        }
        // 最终保留 scale=1
        return totalScore.setScale(1, RoundingMode.HALF_UP);
    }
}
