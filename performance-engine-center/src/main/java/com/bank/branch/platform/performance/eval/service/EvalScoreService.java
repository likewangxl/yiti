package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.entity.EvalRuleGroup;
import com.bank.branch.platform.performance.eval.entity.EvalScore;
import com.bank.branch.platform.performance.eval.entity.EvalTask;
import com.bank.branch.platform.performance.eval.entity.EvalTaskTarget;
import com.bank.branch.platform.performance.eval.mapper.EvalRuleGroupMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalScoreMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalTaskMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalTaskTargetMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalUserTagMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价打分服务.
 * <p>负责打分提交的前置校验（分数范围、任务状态、权限、唯一性）及记录持久化。</p>
 */
@Slf4j
@Service
public class EvalScoreService {

    private final EvalScoreMapper evalScoreMapper;
    private final EvalTaskMapper evalTaskMapper;
    private final EvalTaskTargetMapper evalTaskTargetMapper;
    private final EvalRuleGroupMapper evalRuleGroupMapper;
    private final EvalUserTagMapper evalUserTagMapper;

    @Autowired
    public EvalScoreService(EvalScoreMapper evalScoreMapper,
                            EvalTaskMapper evalTaskMapper,
                            EvalTaskTargetMapper evalTaskTargetMapper,
                            EvalRuleGroupMapper evalRuleGroupMapper,
                            EvalUserTagMapper evalUserTagMapper) {
        this.evalScoreMapper = evalScoreMapper;
        this.evalTaskMapper = evalTaskMapper;
        this.evalTaskTargetMapper = evalTaskTargetMapper;
        this.evalRuleGroupMapper = evalRuleGroupMapper;
        this.evalUserTagMapper = evalUserTagMapper;
    }

    /**
     * 提交评价打分.
     *
     * <p>校验顺序：
     * <ol>
     *   <li>分数范围 10~100，否则抛 EVAL_SCORE_OUT_OF_RANGE</li>
     *   <li>任务 status==0 且 endTime > now，否则抛 EVAL_TASK_CLOSED</li>
     *   <li>查 EvalTaskTarget 确认 target 属于该 task</li>
     *   <li>根据规则组判断评价人权限（groupType=1 比对标签，groupType=2 暂跳过）</li>
     *   <li>唯一性校验，已评过抛 EVAL_SCORE_DUPLICATE</li>
     *   <li>插入 EvalScore 记录</li>
     * </ol>
     *
     * @param taskId     评价任务ID
     * @param targetId   被评价人明细ID
     * @param evalUserId 评价人 USER_ID
     * @param score      打分 10~100
     * @throws PerfException 各类业务异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitScore(Long taskId, Long targetId, Long evalUserId, int score) {
        // 1. 校验分数范围
        if (score < 10 || score > 100) {
            throw new PerfException(PerfErrorCode.EVAL_SCORE_OUT_OF_RANGE, score);
        }

        // 2. 校验任务状态：status==0 且 endTime > now
        EvalTask task = evalTaskMapper.selectById(taskId);
        if (task == null || task.getStatus() != 0 || !task.getEndTime().isAfter(LocalDateTime.now())) {
            throw new PerfException(PerfErrorCode.EVAL_TASK_CLOSED, taskId);
        }

        // 3. 查 EvalTaskTarget，确认 target 属于该 task
        EvalTaskTarget target = evalTaskTargetMapper.selectById(targetId);
        if (target == null || !taskId.equals(target.getTaskId())) {
            throw new PerfException(PerfErrorCode.EVAL_RULE_NOT_FOUND, targetId);
        }

        // 4. 解析评价人所属 group：查 target.ruleId 对应 groups，匹配评价人标签
        List<EvalRuleGroup> groups = evalRuleGroupMapper.selectByRuleId(target.getRuleId());
        Long matchedGroupId = resolveGroupId(groups, evalUserId);
        if (matchedGroupId == null) {
            throw new PerfException(PerfErrorCode.EVAL_NO_PERMISSION, evalUserId);
        }

        // 5. 唯一性校验：同一 target + evalUser 不可重复打分
        int existCount = evalScoreMapper.countByTargetIdAndEvalUserId(targetId, evalUserId);
        if (existCount > 0) {
            throw new PerfException(PerfErrorCode.EVAL_SCORE_DUPLICATE, evalUserId);
        }

        // 6. 插入打分记录
        EvalScore evalScore = new EvalScore();
        evalScore.setTaskId(taskId);
        evalScore.setTargetId(targetId);
        evalScore.setEvalUserId(evalUserId);
        evalScore.setGroupId(matchedGroupId);
        evalScore.setScore(score);
        evalScore.setSubmitTime(LocalDateTime.now());
        evalScoreMapper.insert(evalScore);

        log.info("[EvalScoreService.submitScore] 打分成功 taskId={} targetId={} evalUserId={} score={} groupId={}",
                taskId, targetId, evalUserId, score, matchedGroupId);
    }

    /**
     * 查询指定任务中某评价人已提交的所有打分记录.
     *
     * @param taskId     评价任务ID
     * @param evalUserId 评价人 USER_ID
     * @return 打分记录列表
     */
    public List<EvalScore> getScoresByTaskAndUser(Long taskId, Long evalUserId) {
        return evalScoreMapper.selectByTaskIdAndEvalUserId(taskId, evalUserId);
    }

    // ──────────────────────────────────────────────────────────
    // 私有方法
    // ──────────────────────────────────────────────────────────

    /**
     * 根据规则组列表匹配评价人所属 groupId.
     *
     * <p>groupType=1：按标签匹配，查询评价人持有的评价人标签（tagType=2），与 group.evalTagId 比对。
     * <p>groupType=2：部门员工组，暂跳过（返回 null 视为不匹配）。
     *
     * @param groups     规则下所有评价人组
     * @param evalUserId 评价人 USER_ID
     * @return 匹配的 groupId，未匹配返回 null
     */
    private Long resolveGroupId(List<EvalRuleGroup> groups, Long evalUserId) {
        if (groups == null || groups.isEmpty()) {
            return null;
        }
        // 提前批量查询评价人的所有评价人标签（tagType=2），避免每组单独查一次
        List<Long> evalUserTagIds = evalUserTagMapper.selectTagIdsByUserIdAndType(evalUserId, 2);

        for (EvalRuleGroup group : groups) {
            if (group.getGroupType() == 1) {
                // 标签匹配：评价人需持有 group.evalTagId
                if (group.getEvalTagId() != null && evalUserTagIds.contains(group.getEvalTagId())) {
                    return group.getGroupId();
                }
            }
            // groupType=2 暂跳过
        }
        return null;
    }
}
