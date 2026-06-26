package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EvalImportCompensation} 单元测试：超时 IMPORTING 批次补偿置失败。
 */
@ExtendWith(MockitoExtension.class)
class EvalImportCompensationTest {

    @Mock EvalAssignBatchMapper batchMapper;
    EvalImportCompensation compensation;

    @BeforeEach
    void setUp() {
        compensation = new EvalImportCompensation(batchMapper);
        compensation.importingTimeoutMin = 10;
    }

    private EvalAssignBatch importing(long id, LocalDateTime createTime) {
        EvalAssignBatch b = new EvalAssignBatch();
        b.setBatchId(id);
        b.setStatus(3);
        b.setCreateTime(createTime);
        return b;
    }

    @Test
    @DisplayName("超时(早于阈值)的 IMPORTING 置 STATUS=4 + 超时提示；未超时的不动")
    void compensate_staleFlippedFreshUntouched() {
        EvalAssignBatch stale = importing(1L, LocalDateTime.now().minusMinutes(20));
        EvalAssignBatch fresh = importing(2L, LocalDateTime.now().minusMinutes(1));
        when(batchMapper.selectList(any())).thenReturn(List.of(stale, fresh));

        int flipped = compensation.compensateStaleImporting();

        assertThat(flipped).isEqualTo(1);
        ArgumentCaptor<EvalAssignBatch> cap = ArgumentCaptor.forClass(EvalAssignBatch.class);
        verify(batchMapper, times(1)).updateById(cap.capture());
        EvalAssignBatch updated = cap.getValue();
        assertThat(updated.getBatchId()).isEqualTo(1L);
        assertThat(updated.getStatus()).isEqualTo(4);
        assertThat(updated.getErrorSummary()).isEqualTo("导入中断或超时，请重传");
    }

    @Test
    @DisplayName("无超时批次 → 不发生任何更新")
    void compensate_noStale_noUpdate() {
        when(batchMapper.selectList(any()))
                .thenReturn(List.of(importing(2L, LocalDateTime.now().minusMinutes(1))));
        assertThat(compensation.compensateStaleImporting()).isZero();
        verify(batchMapper, org.mockito.Mockito.never()).updateById(any(EvalAssignBatch.class));
    }

    @Test
    @DisplayName("ApplicationRunner.run 启动时触发一次补偿扫描")
    void run_triggersCompensateOnStartup() {
        when(batchMapper.selectList(any()))
                .thenReturn(List.of(importing(1L, LocalDateTime.now().minusMinutes(30))));

        compensation.run(null);

        verify(batchMapper, times(1)).updateById(any(EvalAssignBatch.class));
    }
}
