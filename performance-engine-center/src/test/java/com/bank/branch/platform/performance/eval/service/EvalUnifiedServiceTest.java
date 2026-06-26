package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalTask;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignItemMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalScoreMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalTaskMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalTaskTargetMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalUnifiedMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvalUnifiedServiceTest {

    @Mock EvalUnifiedMapper unifiedMapper;
    @Mock EvalTaskMapper evalTaskMapper;
    @Mock EvalTaskTargetMapper evalTaskTargetMapper;
    @Mock EvalScoreMapper evalScoreMapper;
    @Mock EvalAssignBatchMapper evalAssignBatchMapper;
    @Mock EvalAssignItemMapper evalAssignItemMapper;
    EvalUnifiedService service;

    @BeforeEach
    void setUp() {
        service = new EvalUnifiedService(unifiedMapper, evalTaskMapper, evalTaskTargetMapper,
                evalScoreMapper, evalAssignBatchMapper, evalAssignItemMapper);
    }

    private EvalAssignBatch batch(Integer status, LocalDateTime deadline) {
        EvalAssignBatch b = new EvalAssignBatch();
        b.setBatchId(7L);
        b.setStatus(status);
        b.setDeadline(deadline);
        return b;
    }

    @Test
    @DisplayName("草稿(未发布)导入批次：截止时间未到也可删除（级联删明细+批次）")
    void deleteUnified_draftBatch_deletableEvenBeforeDeadline() {
        // status=2(DRAFT)，截止时间在未来
        when(evalAssignBatchMapper.selectById(7L)).thenReturn(batch(2, LocalDateTime.now().plusDays(3)));

        service.deleteUnified("IMPORT", 7L);

        verify(evalAssignItemMapper).deleteByBatchId(7L);
        verify(evalAssignBatchMapper).deleteById(7L);
    }

    @Test
    @DisplayName("已发布(进行中)导入批次：截止时间未到仍拒绝删除")
    void deleteUnified_activeBatchBeforeDeadline_rejected() {
        // status=0(ACTIVE)，截止时间在未来
        when(evalAssignBatchMapper.selectById(7L)).thenReturn(batch(0, LocalDateTime.now().plusDays(3)));

        assertThatThrownBy(() -> service.deleteUnified("IMPORT", 7L))
                .isInstanceOf(PerfException.class)
                .hasFieldOrPropertyWithValue("code", PerfErrorCode.EVAL_TASK_DELETE_BEFORE_DEADLINE.getCode());

        verify(evalAssignItemMapper, never()).deleteByBatchId(7L);
        verify(evalAssignBatchMapper, never()).deleteById(7L);
    }

    @Test
    @DisplayName("已发布导入批次：截止时间已过可删除")
    void deleteUnified_activeBatchAfterDeadline_deletable() {
        when(evalAssignBatchMapper.selectById(7L)).thenReturn(batch(0, LocalDateTime.now().minusDays(1)));

        service.deleteUnified("IMPORT", 7L);

        verify(evalAssignItemMapper).deleteByBatchId(7L);
        verify(evalAssignBatchMapper).deleteById(7L);
    }

    @Test
    @DisplayName("规则任务：截止时间未到仍拒绝删除（规则任务无草稿态）")
    void deleteUnified_autoTaskBeforeDeadline_rejected() {
        EvalTask task = new EvalTask();
        task.setTaskId(5L);
        task.setEndTime(LocalDateTime.now().plusDays(2));
        when(evalTaskMapper.selectById(5L)).thenReturn(task);

        assertThatThrownBy(() -> service.deleteUnified("AUTO", 5L))
                .isInstanceOf(PerfException.class)
                .hasFieldOrPropertyWithValue("code", PerfErrorCode.EVAL_TASK_DELETE_BEFORE_DEADLINE.getCode());

        verify(evalTaskMapper, never()).deleteById(5L);
    }
}
