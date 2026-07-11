package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.dto.DictItemDTO;
import com.bank.branch.platform.performance.eval.dto.EvalRewardPendingGroupDTO;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalRewardItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalRewardItemMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EvalRewardService} 单元测试（奖励分配用户端：汇总/明细/一次性提交）。
 */
@ExtendWith(MockitoExtension.class)
class EvalRewardServiceTest {

    @Mock EvalRewardItemMapper itemMapper;
    @Mock EvalAssignBatchMapper batchMapper;
    @Mock DictApi dictApi;
    EvalRewardService service;

    @BeforeEach
    void setUp() {
        service = new EvalRewardService(itemMapper, batchMapper, dictApi);
    }

    private EvalAssignBatch activeBatch() {
        EvalAssignBatch b = new EvalAssignBatch();
        b.setBatchId(5L);
        b.setStatus(0); // ACTIVE
        b.setDeadline(LocalDateTime.now().plusDays(3));
        return b;
    }

    private EvalRewardItem item(long id, String dept, String total) {
        EvalRewardItem it = new EvalRewardItem();
        it.setItemId(id);
        it.setBatchId(5L);
        it.setAssignUserId("A1");
        it.setDeptName(dept);
        it.setAssignTotal(new BigDecimal(total));
        it.setSubmitted(0);
        return it;
    }

    private EvalRewardService.RewardEntry entry(long id, String v) {
        return new EvalRewardService.RewardEntry(id, new BigDecimal(v));
    }

    @Test
    @DisplayName("汇总：taskTypeLabel 经字典翻译")
    void listGroups_translatesLabel() {
        EvalRewardPendingGroupDTO g = new EvalRewardPendingGroupDTO();
        g.setTaskType("REWARD");
        when(itemMapper.selectRewardPendingGroups("A1")).thenReturn(List.of(g));
        DictItemDTO d = new DictItemDTO();
        d.setDictCode("REWARD");
        d.setDictLabel("奖励分配");
        when(dictApi.getDictItems("EVAL_IMPORT_TYPE")).thenReturn(List.of(d));

        List<EvalRewardPendingGroupDTO> out = service.listMyRewardPendingGroups("A1");

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getTaskTypeLabel()).isEqualTo("奖励分配");
    }

    @Test
    @DisplayName("提交成功：每人>0 且 求和=分配合计 → 全部 markAssigned")
    void submit_success() {
        when(batchMapper.selectById(5L)).thenReturn(activeBatch());
        when(itemMapper.selectByAssignerBatchDept("A1", 5L, "信贷部"))
                .thenReturn(List.of(item(11L, "信贷部", "100"), item(12L, "信贷部", "100")));
        when(itemMapper.markAssigned(any(), any(), any())).thenReturn(1);

        service.submitRewardBatch("A1", 5L, "信贷部", List.of(entry(11L, "60"), entry(12L, "40")));

        verify(itemMapper).markAssigned(eq(11L), eq(new BigDecimal("60")), any());
        verify(itemMapper).markAssigned(eq(12L), eq(new BigDecimal("40")), any());
    }

    @Test
    @DisplayName("提交失败：某人分配值<=0 → 分配值必须大于0，一条不写")
    void submit_notPositive() {
        when(batchMapper.selectById(5L)).thenReturn(activeBatch());
        when(itemMapper.selectByAssignerBatchDept("A1", 5L, "信贷部"))
                .thenReturn(List.of(item(11L, "信贷部", "100"), item(12L, "信贷部", "100")));
        assertThatThrownBy(() -> service.submitRewardBatch("A1", 5L, "信贷部",
                List.of(entry(11L, "100"), entry(12L, "0"))))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("分配值必须大于0");
        verify(itemMapper, never()).markAssigned(any(), any(), any());
    }

    @Test
    @DisplayName("提交失败：求和≠分配合计 → 分配值之和必须等于分配合计")
    void submit_sumMismatch() {
        when(batchMapper.selectById(5L)).thenReturn(activeBatch());
        when(itemMapper.selectByAssignerBatchDept("A1", 5L, "信贷部"))
                .thenReturn(List.of(item(11L, "信贷部", "100"), item(12L, "信贷部", "100")));
        assertThatThrownBy(() -> service.submitRewardBatch("A1", 5L, "信贷部",
                List.of(entry(11L, "60"), entry(12L, "50")))) // 和=110≠100
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("分配值之和必须等于分配合计");
        verify(itemMapper, never()).markAssigned(any(), any(), any());
    }

    @Test
    @DisplayName("提交失败：未覆盖该组全部未提交明细 → 报错，一条不写")
    void submit_notCoverAll() {
        when(batchMapper.selectById(5L)).thenReturn(activeBatch());
        when(itemMapper.selectByAssignerBatchDept("A1", 5L, "信贷部"))
                .thenReturn(List.of(item(11L, "信贷部", "100"), item(12L, "信贷部", "100")));
        assertThatThrownBy(() -> service.submitRewardBatch("A1", 5L, "信贷部", List.of(entry(11L, "100"))))
                .isInstanceOf(PerfException.class);
        verify(itemMapper, never()).markAssigned(any(), any(), any());
    }

    @Test
    @DisplayName("提交失败：批次已过截止 → 任务已结束")
    void submit_deadlinePassed() {
        EvalAssignBatch b = activeBatch();
        b.setDeadline(LocalDateTime.now().minusDays(1));
        when(batchMapper.selectById(5L)).thenReturn(b);
        assertThatThrownBy(() -> service.submitRewardBatch("A1", 5L, "信贷部", List.of(entry(11L, "100"))))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("任务已结束");
    }

    @Test
    @DisplayName("提交失败：批次非 ACTIVE → 批次未发布或已结束")
    void submit_notActive() {
        EvalAssignBatch b = activeBatch();
        b.setStatus(2); // DRAFT
        when(batchMapper.selectById(5L)).thenReturn(b);
        assertThatThrownBy(() -> service.submitRewardBatch("A1", 5L, "信贷部", List.of(entry(11L, "100"))))
                .isInstanceOf(PerfException.class);
    }

    @Test
    @DisplayName("明细：实体映射为 DTO（含原始值/兑现值/分配合计）")
    void listItems_mapsDto() {
        EvalRewardItem it = item(11L, "信贷部", "100");
        it.setBeAssignedUserId("B1");
        it.setBeAssignedUserName("被一");
        it.setOriginalValue(new BigDecimal("76.5"));
        it.setCashValue(new BigDecimal("80"));
        when(itemMapper.selectByAssignerBatchDept("A1", 5L, "信贷部")).thenReturn(List.of(it));

        var out = service.listMyRewardPendingItems("A1", 5L, "信贷部");

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getBeAssignedUserId()).isEqualTo("B1");
        assertThat(out.get(0).getOriginalValue()).isEqualByComparingTo("76.5");
        assertThat(out.get(0).getAssignTotal()).isEqualByComparingTo("100");
    }
}
