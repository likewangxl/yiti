package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
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
import java.util.Set;

/**
 * 评价打分服务.
 * <p>负责打分提交的前置校验（分数范围、任务状态、权限、唯一性）及记录持久化。</p>
 */
@Slf4j
@Service
public class EvalScoreService {

    /** 等级打分模式下允许的固定分值：非常满意100/比较满意95/满意85/一般75/不满意59. */
    private static final Set<Integer> GRADE_SCORES = Set.of(100, 95, 85, 75, 59);

    private final EvalScoreMapper evalScoreMapper;
    private final EvalTaskMapper evalTaskMapper;
    private final EvalTaskTargetMapper evalTaskTargetMapper;
    private final EvalRuleGroupMapper evalRuleGroupMapper;
    private final EvalUserTagMapper evalUserTagMapper;
    private final OrgApi orgApi;

    @Autowired
    public EvalScoreService(EvalScoreMapper evalScoreMapper,
                            EvalTaskMapper evalTaskMapper,
                            EvalTaskTargetMapper evalTaskTargetMapper,
                            EvalRuleGroupMapper evalRuleGroupMapper,
                            EvalUserTagMapper evalUserTagMapper,
                            OrgApi orgApi) {
        this.evalScoreMapper = evalScoreMapper;
        this.evalTaskMapper = evalTaskMapper;
        this.evalTaskTargetMapper = evalTaskTargetMapper;
        this.evalRuleGroupMapper = evalRuleGroupMapper;
        this.evalUserTagMapper = evalUserTagMapper;
        this.orgApi = orgApi;
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
    public void submitScore(Long taskId, Long targetId, String evalUserId, int score) {
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

        // 4. 解析评价人所属 group：查 target.ruleId 对应 groups，匹配评价人标签或同部门
        List<EvalRuleGroup> groups = evalRuleGroupMapper.selectByRuleId(target.getRuleId());
        EvalRuleGroup matchedGroup = resolveGroup(groups, evalUserId, target.getBeEvalUserId());
        if (matchedGroup == null) {
            throw new PerfException(PerfErrorCode.EVAL_NO_PERMISSION, evalUserId);
        }

        // 4.1 等级打分模式：分数必须为预设等级值之一
        if (matchedGroup.getScoreMode() != null && matchedGroup.getScoreMode() == 2
                && !GRADE_SCORES.contains(score)) {
            throw new PerfException(PerfErrorCode.EVAL_SCORE_OUT_OF_RANGE, score);
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
        evalScore.setGroupId(matchedGroup.getGroupId());
        evalScore.setScore(score);
        evalScore.setSubmitTime(LocalDateTime.now());
        evalScoreMapper.insert(evalScore);

        log.info("[EvalScoreService.submitScore] 打分成功 taskId={} targetId={} evalUserId={} score={} groupId={} scoreMode={}",
                taskId, targetId, evalUserId, score, matchedGroup.getGroupId(), matchedGroup.getScoreMode());
    }

    /**
     * 查询指定任务中某评价人已提交的所有打分记录.
     *
     * @param taskId     评价任务ID
     * @param evalUserId 评价人 USER_ID
     * @return 打分记录列表
     */
    public List<EvalScore> getScoresByTaskAndUser(Long taskId, String evalUserId) {
        return evalScoreMapper.selectByTaskIdAndEvalUserId(taskId, evalUserId);
    }

    // ──────────────────────────────────────────────────────────
    // 私有方法
    // ──────────────────────────────────────────────────────────

    /**
     * 解析当前评价人对某被评价人适用的评分方式.
     *
     * @param ruleId       规则ID
     * @param evalUserId   评价人 USER_ID
     * @param beEvalUserId 被评价人 USER_ID
     * @return 评分方式（1=数值, 2=等级），无匹配时返回 1（默认数值）
     */
    public Integer resolveScoreModeForUser(Long ruleId, String evalUserId, String beEvalUserId) {
        List<EvalRuleGroup> groups = evalRuleGroupMapper.selectByRuleId(ruleId);
        EvalRuleGroup matched = resolveGroup(groups, evalUserId, beEvalUserId);
        return (matched != null && matched.getScoreMode() != null) ? matched.getScoreMode() : 1;
    }

    /**
     * 根据规则组列表匹配评价人所属组.
     *
     * <p>groupType=1：按标签匹配，查询评价人持有的评价人标签（role_type=2），与 group.evalTagId 比对。
     * <p>groupType=2：部门员工组，通过 OrgApi 判断评价人与被评价人是否同部门。
     *
     * @param groups       规则下所有评价人组
     * @param evalUserId   评价人 USER_ID
     * @param beEvalUserId 被评价人 USER_ID
     * @return 匹配的评价人组实体，未匹配返回 null
     */
    private EvalRuleGroup resolveGroup(List<EvalRuleGroup> groups, String evalUserId, String beEvalUserId) {
        if (groups == null || groups.isEmpty()) {
            return null;
        }
        List<Long> evalUserTagIds = evalUserTagMapper.selectTagIdsByUserIdAndType(evalUserId, 2);

        for (EvalRuleGroup group : groups) {
            if (group.getGroupType() == 1) {
                if (group.getEvalTagId() != null && evalUserTagIds.contains(group.getEvalTagId())) {
                    return group;
                }
            } else if (group.getGroupType() == 2) {
                if (isSameOrg(evalUserId, beEvalUserId)) {
                    return group;
                }
            }
        }
        return null;
    }

    /**
     * 判断两个用户是否属于同一部门.
     */
    private boolean isSameOrg(String userIdA, String userIdB) {
        try {
            OrgDTO orgA = orgApi.getUserMainOrg(userIdA);
            OrgDTO orgB = orgApi.getUserMainOrg(userIdB);
            if (orgA == null || orgB == null) {
                return false;
            }
            return orgA.getOrgCode() != null && orgA.getOrgCode().equals(orgB.getOrgCode());
        } catch (Exception e) {
            log.warn("[EvalScoreService.isSameOrg] OrgApi 查询异常，降级为不匹配 userA={} userB={}", userIdA, userIdB, e);
            return false;
        }
    }
}
