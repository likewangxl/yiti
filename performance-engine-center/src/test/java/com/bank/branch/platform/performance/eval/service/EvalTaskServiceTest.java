package com.bank.branch.platform.performance.eval.service;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EvalTaskService 单元测试.
 * 覆盖：截止时间校验、三组加权平均得分计算、某组无人打分贡献0。
 */
@ExtendWith(MockitoExtension.class)
class EvalTaskServiceTest {

    @Mock
    EvalTaskMapper evalTaskMapper;
    @Mock
    EvalTaskTargetMapper evalTaskTargetMapper;
    @Mock
    EvalRuleMapper evalRuleMapper;
    @Mock
    EvalRuleGroupMapper evalRuleGroupMapper;
    @Mock
    EvalScoreMapper evalScoreMapper;
    @Mock
    EvalUserTagMapper evalUserTagMapper;

    @InjectMocks
    EvalTaskService evalTaskService;

    // ─────────────────────────────────────────────
    // 1. 截止时间早于当前 → EVAL_TASK_END_TIME_INVALID
    // ─────────────────────────────────────────────

    @Test
    void createTask_endTimeInPast_throwsEndTimeInvalid() {
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(1);

        assertThatThrownBy(() ->
                evalTaskService.createTask("任务A", pastTime, List.of("101"), "1"))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.EVAL_TASK_END_TIME_INVALID);
    }

    // ─────────────────────────────────────────────
    // 2. 三组加权平均：g1(50%,1人80)=40 + g2(30%,3人avg=80)=24 + g3(20%,2人avg=85)=17 → 81.0
    // ─────────────────────────────────────────────

    @Test
    void calculateScores_threeGroups_correctWeightedAverage() {
        Long taskId = 1L;
        Long targetId = 10L;
        Long ruleId = 200L;

        // target
        EvalTaskTarget target = new EvalTaskTarget();
        target.setTargetId(targetId);
        target.setTaskId(taskId);
        target.setRuleId(ruleId);
        when(evalTaskTargetMapper.selectByTaskId(taskId)).thenReturn(List.of(target));

        // 三个 group
        EvalRuleGroup g1 = group(301L, ruleId, 1, 50);
        EvalRuleGroup g2 = group(302L, ruleId, 1, 30);
        EvalRuleGroup g3 = group(303L, ruleId, 1, 20);
        when(evalRuleGroupMapper.selectByRuleId(ruleId)).thenReturn(List.of(g1, g2, g3));

        // scores：g1=80, g2=70/80/90(avg80), g3=80/90(avg85)
        EvalScore s1 = score(targetId, 301L, 80);
        EvalScore s2a = score(targetId, 302L, 70);
        EvalScore s2b = score(targetId, 302L, 80);
        EvalScore s2c = score(targetId, 302L, 90);
        EvalScore s3a = score(targetId, 303L, 80);
        EvalScore s3b = score(targetId, 303L, 90);
        when(evalScoreMapper.selectByTargetId(targetId))
                .thenReturn(List.of(s1, s2a, s2b, s2c, s3a, s3b));

        // Act
        evalTaskService.calculateScoresForTask(taskId);

        // Assert：40 + 24 + 17 = 81.0
        verify(evalTaskTargetMapper).updateFinalScore(
                eq(targetId), eq(new BigDecimal("81.0")));
    }

    // ─────────────────────────────────────────────
    // 3. 某组无人打分 → 贡献 0；g1(60%,1人80)=48 + g2(40%,无人)=0 → 48.0
    // ─────────────────────────────────────────────

    @Test
    void calculateScores_oneGroupEmpty_contributes0() {
        Long taskId = 2L;
        Long targetId = 20L;
        Long ruleId = 210L;

        EvalTaskTarget target = new EvalTaskTarget();
        target.setTargetId(targetId);
        target.setTaskId(taskId);
        target.setRuleId(ruleId);
        when(evalTaskTargetMapper.selectByTaskId(taskId)).thenReturn(List.of(target));

        EvalRuleGroup g1 = group(401L, ruleId, 1, 60);
        EvalRuleGroup g2 = group(402L, ruleId, 1, 40);
        when(evalRuleGroupMapper.selectByRuleId(ruleId)).thenReturn(List.of(g1, g2));

        // 只有 g1 有打分，g2 无打分
        EvalScore s1 = score(targetId, 401L, 80);
        when(evalScoreMapper.selectByTargetId(targetId)).thenReturn(List.of(s1));

        // Act
        evalTaskService.calculateScoresForTask(taskId);

        // Assert：48 + 0 = 48.0
        verify(evalTaskTargetMapper).updateFinalScore(
                eq(targetId), eq(new BigDecimal("48.0")));
    }

    // ─────────────────────────────────────────────
    // 4. createTask：用单标签匹配规则生成 target
    // ─────────────────────────────────────────────

    @Test
    void createTask_singleTag_matchesRule() {
        java.time.LocalDateTime future = java.time.LocalDateTime.now().plusDays(1);
        com.bank.branch.platform.performance.eval.entity.EvalRule rule =
                new com.bank.branch.platform.performance.eval.entity.EvalRule();
        rule.setRuleId(200L);
        rule.setBeEvalTagId(5L);
        rule.setStatus(1);
        when(evalUserTagMapper.selectTagIdByUserId("101")).thenReturn(5L);
        when(evalRuleMapper.selectByBeEvalTagId(5L)).thenReturn(rule);

        evalTaskService.createTask("任务A", future, List.of("101"), "1");

        verify(evalTaskTargetMapper).batchInsert(org.mockito.ArgumentMatchers.argThat(
                list -> list.size() == 1 && list.get(0).getRuleId().equals(200L)
                        && list.get(0).getBeEvalUserId().equals("101")));
    }

    @Test
    void createTask_noTag_skips() {
        java.time.LocalDateTime future = java.time.LocalDateTime.now().plusDays(1);
        when(evalUserTagMapper.selectTagIdByUserId("101")).thenReturn(null);

        evalTaskService.createTask("任务A", future, List.of("101"), "1");

        verify(evalTaskTargetMapper, org.mockito.Mockito.never())
                .batchInsert(org.mockito.ArgumentMatchers.anyList());
    }

    // ──────────────────────────────────────────────────────────
    // 辅助方法
    // ──────────────────────────────────────────────────────────

    private EvalRuleGroup group(Long groupId, Long ruleId, int groupType, int weightInt) {
        EvalRuleGroup g = new EvalRuleGroup();
        g.setGroupId(groupId);
        g.setRuleId(ruleId);
        g.setGroupType(groupType);
        g.setWeight(new BigDecimal(weightInt));
        return g;
    }

    private EvalScore score(Long targetId, Long groupId, int scoreVal) {
        EvalScore s = new EvalScore();
        s.setTargetId(targetId);
        s.setGroupId(groupId);
        s.setScore(scoreVal);
        return s;
    }
}
