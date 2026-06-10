package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.entity.EvalScore;
import com.bank.branch.platform.performance.eval.entity.EvalTask;
import com.bank.branch.platform.performance.eval.entity.EvalTaskTarget;
import com.bank.branch.platform.performance.eval.mapper.EvalRuleGroupMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalScoreMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalTaskMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalTaskTargetMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalUserTagMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EvalScoreService 单元测试.
 * 覆盖：打分成功、分数越界（低/高）、任务已结束（status=1 / endTime 过期）、重复评价。
 */
@ExtendWith(MockitoExtension.class)
class EvalScoreServiceTest {

    @Mock
    EvalScoreMapper evalScoreMapper;
    @Mock
    EvalTaskMapper evalTaskMapper;
    @Mock
    EvalTaskTargetMapper evalTaskTargetMapper;
    @Mock
    EvalRuleGroupMapper evalRuleGroupMapper;
    @Mock
    EvalUserTagMapper evalUserTagMapper;
    @Mock
    OrgApi orgApi;

    @InjectMocks
    EvalScoreService evalScoreService;

    // ─────────────────────────────────────────────
    // 1. 打分成功
    // ─────────────────────────────────────────────

    @Test
    void submitScore_success_insertsRecord() {
        // Arrange
        Long taskId = 1L;
        Long targetId = 10L;
        String evalUserId = "100";
        int score = 80;

        EvalTask task = new EvalTask();
        task.setTaskId(taskId);
        task.setStatus(0); // 进行中
        task.setEndTime(LocalDateTime.now().plusDays(1));
        when(evalTaskMapper.selectById(taskId)).thenReturn(task);

        EvalTaskTarget target = new EvalTaskTarget();
        target.setTargetId(targetId);
        target.setTaskId(taskId);
        target.setRuleId(200L);
        when(evalTaskTargetMapper.selectById(targetId)).thenReturn(target);

        // groupType=1，evalTagId=50，评价人持有该标签，数值打分
        com.bank.branch.platform.performance.eval.entity.EvalRuleGroup group =
                new com.bank.branch.platform.performance.eval.entity.EvalRuleGroup();
        group.setGroupId(300L);
        group.setRuleId(200L);
        group.setGroupType(1);
        group.setEvalTagId(50L);
        group.setScoreMode(1);
        when(evalRuleGroupMapper.selectByRuleId(200L)).thenReturn(List.of(group));

        // 评价人持有 tagId=50（评价人标签 tagType=2）
        when(evalUserTagMapper.selectTagIdByUserId(evalUserId))
                .thenReturn(50L);

        // 未重复
        when(evalScoreMapper.countByTargetIdAndEvalUserId(targetId, evalUserId)).thenReturn(0);
        when(evalScoreMapper.insert(any(EvalScore.class))).thenReturn(1);

        // Act
        evalScoreService.submitScore(taskId, targetId, evalUserId, score);

        // Assert：insert 被调用，分数与 groupId 正确
        ArgumentCaptor<EvalScore> captor = ArgumentCaptor.forClass(EvalScore.class);
        verify(evalScoreMapper).insert((EvalScore) captor.capture());
        EvalScore saved = captor.getValue();
        assertThat(saved.getScore()).isEqualTo(score);
        assertThat(saved.getTaskId()).isEqualTo(taskId);
        assertThat(saved.getTargetId()).isEqualTo(targetId);
        assertThat(saved.getEvalUserId()).isEqualTo(evalUserId);
        assertThat(saved.getGroupId()).isEqualTo(300L);
    }

    // ─────────────────────────────────────────────
    // 2. 分数低于 10 → EVAL_SCORE_OUT_OF_RANGE
    // ─────────────────────────────────────────────

    @Test
    void submitScore_scoreBelowMin_throwsOutOfRange() {
        assertThatThrownBy(() -> evalScoreService.submitScore(1L, 10L, "100", 9))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.EVAL_SCORE_OUT_OF_RANGE);
    }

    // ─────────────────────────────────────────────
    // 3. 分数高于 100 → EVAL_SCORE_OUT_OF_RANGE
    // ─────────────────────────────────────────────

    @Test
    void submitScore_scoreAboveMax_throwsOutOfRange() {
        assertThatThrownBy(() -> evalScoreService.submitScore(1L, 10L, "100", 101))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.EVAL_SCORE_OUT_OF_RANGE);
    }

    // ─────────────────────────────────────────────
    // 4. 任务 status=1（已结束）→ EVAL_TASK_CLOSED
    // ─────────────────────────────────────────────

    @Test
    void submitScore_taskStatusClosed_throwsTaskClosed() {
        Long taskId = 1L;
        EvalTask task = new EvalTask();
        task.setTaskId(taskId);
        task.setStatus(1); // 已结束
        task.setEndTime(LocalDateTime.now().plusDays(1));
        when(evalTaskMapper.selectById(taskId)).thenReturn(task);

        assertThatThrownBy(() -> evalScoreService.submitScore(taskId, 10L, "100", 80))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.EVAL_TASK_CLOSED);
    }

    // ─────────────────────────────────────────────
    // 5. 任务 endTime < now（过期）→ EVAL_TASK_CLOSED
    // ─────────────────────────────────────────────

    @Test
    void submitScore_taskExpired_throwsTaskClosed() {
        Long taskId = 2L;
        EvalTask task = new EvalTask();
        task.setTaskId(taskId);
        task.setStatus(0);
        task.setEndTime(LocalDateTime.now().minusSeconds(1)); // 已过期
        when(evalTaskMapper.selectById(taskId)).thenReturn(task);

        assertThatThrownBy(() -> evalScoreService.submitScore(taskId, 10L, "100", 80))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.EVAL_TASK_CLOSED);
    }

    // ─────────────────────────────────────────────
    // 6. 重复评价 → EVAL_SCORE_DUPLICATE
    // ─────────────────────────────────────────────

    @Test
    void submitScore_duplicate_throwsScoreDuplicate() {
        Long taskId = 1L;
        Long targetId = 10L;
        String evalUserId = "100";

        EvalTask task = new EvalTask();
        task.setTaskId(taskId);
        task.setStatus(0);
        task.setEndTime(LocalDateTime.now().plusDays(1));
        when(evalTaskMapper.selectById(taskId)).thenReturn(task);

        EvalTaskTarget target = new EvalTaskTarget();
        target.setTargetId(targetId);
        target.setTaskId(taskId);
        target.setRuleId(200L);
        when(evalTaskTargetMapper.selectById(targetId)).thenReturn(target);

        com.bank.branch.platform.performance.eval.entity.EvalRuleGroup group =
                new com.bank.branch.platform.performance.eval.entity.EvalRuleGroup();
        group.setGroupId(300L);
        group.setRuleId(200L);
        group.setGroupType(1);
        group.setEvalTagId(50L);
        group.setScoreMode(1);
        when(evalRuleGroupMapper.selectByRuleId(200L)).thenReturn(List.of(group));

        when(evalUserTagMapper.selectTagIdByUserId(evalUserId))
                .thenReturn(50L);

        // 已有记录 → 重复
        when(evalScoreMapper.countByTargetIdAndEvalUserId(targetId, evalUserId)).thenReturn(1);

        assertThatThrownBy(() -> evalScoreService.submitScore(taskId, targetId, evalUserId, 80))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.EVAL_SCORE_DUPLICATE);
    }

    // ─────────────────────────────────────────────
    // 7. 等级打分模式：合法等级分（95）→ 成功
    // ─────────────────────────────────────────────

    @Test
    void submitScore_gradeMode_validGradeScore_success() {
        Long taskId = 1L;
        Long targetId = 10L;
        String evalUserId = "100";

        EvalTask task = new EvalTask();
        task.setTaskId(taskId);
        task.setStatus(0);
        task.setEndTime(LocalDateTime.now().plusDays(1));
        when(evalTaskMapper.selectById(taskId)).thenReturn(task);

        EvalTaskTarget target = new EvalTaskTarget();
        target.setTargetId(targetId);
        target.setTaskId(taskId);
        target.setRuleId(200L);
        when(evalTaskTargetMapper.selectById(targetId)).thenReturn(target);

        com.bank.branch.platform.performance.eval.entity.EvalRuleGroup group =
                new com.bank.branch.platform.performance.eval.entity.EvalRuleGroup();
        group.setGroupId(300L);
        group.setRuleId(200L);
        group.setGroupType(1);
        group.setEvalTagId(50L);
        group.setScoreMode(2); // 等级打分
        when(evalRuleGroupMapper.selectByRuleId(200L)).thenReturn(List.of(group));

        when(evalUserTagMapper.selectTagIdByUserId(evalUserId))
                .thenReturn(50L);
        when(evalScoreMapper.countByTargetIdAndEvalUserId(targetId, evalUserId)).thenReturn(0);
        when(evalScoreMapper.insert(any(EvalScore.class))).thenReturn(1);

        // 95 = "比较满意"，是合法等级分
        evalScoreService.submitScore(taskId, targetId, evalUserId, 95);

        ArgumentCaptor<EvalScore> captor = ArgumentCaptor.forClass(EvalScore.class);
        verify(evalScoreMapper).insert(captor.capture());
        assertThat(captor.getValue().getScore()).isEqualTo(95);
    }

    // ─────────────────────────────────────────────
    // 8. 等级打分模式：非法分值（80）→ EVAL_SCORE_OUT_OF_RANGE
    // ─────────────────────────────────────────────

    @Test
    void submitScore_gradeMode_invalidGradeScore_throwsOutOfRange() {
        Long taskId = 1L;
        Long targetId = 10L;
        String evalUserId = "100";

        EvalTask task = new EvalTask();
        task.setTaskId(taskId);
        task.setStatus(0);
        task.setEndTime(LocalDateTime.now().plusDays(1));
        when(evalTaskMapper.selectById(taskId)).thenReturn(task);

        EvalTaskTarget target = new EvalTaskTarget();
        target.setTargetId(targetId);
        target.setTaskId(taskId);
        target.setRuleId(200L);
        when(evalTaskTargetMapper.selectById(targetId)).thenReturn(target);

        com.bank.branch.platform.performance.eval.entity.EvalRuleGroup group =
                new com.bank.branch.platform.performance.eval.entity.EvalRuleGroup();
        group.setGroupId(300L);
        group.setRuleId(200L);
        group.setGroupType(1);
        group.setEvalTagId(50L);
        group.setScoreMode(2); // 等级打分
        when(evalRuleGroupMapper.selectByRuleId(200L)).thenReturn(List.of(group));

        when(evalUserTagMapper.selectTagIdByUserId(evalUserId))
                .thenReturn(50L);

        // 80 不在 {100, 95, 85, 75, 59} 中
        assertThatThrownBy(() -> evalScoreService.submitScore(taskId, targetId, evalUserId, 80))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.EVAL_SCORE_OUT_OF_RANGE);
    }
}
