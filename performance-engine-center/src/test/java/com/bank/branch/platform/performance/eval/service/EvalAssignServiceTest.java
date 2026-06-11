package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.dto.DictItemDTO;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.dto.EvalPendingGroupDTO;
import com.bank.branch.platform.performance.eval.dto.EvalPendingItemDTO;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalAssignItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignItemMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvalAssignServiceTest {

    @Mock EvalAssignItemMapper itemMapper;
    @Mock EvalAssignBatchMapper batchMapper;
    @Mock DictApi dictApi;
    EvalAssignService service;

    @BeforeEach
    void setUp() {
        service = new EvalAssignService(itemMapper, batchMapper, dictApi);
    }

    private EvalAssignItem item(Long id, String scorer, String scoreType, Integer submitted, Long batchId) {
        EvalAssignItem i = new EvalAssignItem();
        i.setItemId(id);
        i.setEvalUserId(scorer);
        i.setScoreType(scoreType);
        i.setSubmitted(submitted);
        i.setBatchId(batchId);
        i.setBeEvalUserId("B1");
        return i;
    }

    private EvalAssignBatch batch(Long id, LocalDateTime deadline) {
        EvalAssignBatch b = new EvalAssignBatch();
        b.setBatchId(id);
        b.setDeadline(deadline);
        b.setStatus(0); // ACTIVE
        return b;
    }

    private DictItemDTO dict(String code, String label) {
        DictItemDTO d = new DictItemDTO();
        d.setDictType("EVAL_IMPORT_TYPE");
        d.setDictCode(code);
        d.setDictLabel(label);
        return d;
    }

    // ---------------- 汇总 / 明细 ----------------

    @Test
    @DisplayName("汇总：用字典翻译任务类型展示名")
    void listMyPendingGroups_fillsTaskTypeLabel() {
        EvalPendingGroupDTO g = new EvalPendingGroupDTO();
        g.setBatchId(1L);
        g.setTaskType("EVAL");
        g.setDept("信贷部");
        g.setPendingCount(3);
        when(itemMapper.selectPendingGroups("E1")).thenReturn(List.of(g));
        when(dictApi.getDictItems("EVAL_IMPORT_TYPE")).thenReturn(List.of(
                dict("EVAL", "评价任务"), dict("REWARD", "奖励分配")));

        List<EvalPendingGroupDTO> out = service.listMyPendingGroups("E1");

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getTaskTypeLabel()).isEqualTo("评价任务");
        assertThat(out.get(0).getPendingCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("明细：实体映射为 DTO")
    void listMyPendingItems_mapsEntities() {
        EvalAssignItem i1 = item(10L, "E1", "NUM", 0, 1L);
        EvalAssignItem i2 = item(11L, "E1", "GRADE", 1, 1L);
        i2.setScore(85);
        when(itemMapper.selectByScorerBatchDept("E1", 1L, "信贷部")).thenReturn(List.of(i1, i2));

        List<EvalPendingItemDTO> out = service.listMyPendingItems("E1", 1L, "信贷部");

        assertThat(out).hasSize(2);
        assertThat(out.get(0).getItemId()).isEqualTo(10L);
        assertThat(out.get(0).getScoreType()).isEqualTo("NUM");
        assertThat(out.get(0).getSubmitted()).isEqualTo(0);
        assertThat(out.get(1).getScore()).isEqualTo(85);
        assertThat(out.get(1).getSubmitted()).isEqualTo(1);
    }

    // ---------------- 提交打分 ----------------

    @Test
    @DisplayName("提交：数值打分合法 → 写库")
    void submit_num_ok() {
        when(itemMapper.selectById(10L)).thenReturn(item(10L, "E1", "NUM", 0, 1L));
        when(batchMapper.selectById(1L)).thenReturn(batch(1L, LocalDateTime.now().plusDays(1)));

        service.submitScore("E1", 10L, 80);

        verify(itemMapper).markSubmitted(eq(10L), eq(80), any());
    }

    @Test
    @DisplayName("提交：等级打分合法值 → 写库")
    void submit_grade_ok() {
        when(itemMapper.selectById(10L)).thenReturn(item(10L, "E1", "GRADE", 0, 1L));
        when(batchMapper.selectById(1L)).thenReturn(batch(1L, LocalDateTime.now().plusDays(1)));

        service.submitScore("E1", 10L, 85);

        verify(itemMapper).markSubmitted(eq(10L), eq(85), any());
    }

    @Test
    @DisplayName("提交：等级打分非预设值 → 超范围")
    void submit_grade_invalidValue() {
        when(itemMapper.selectById(10L)).thenReturn(item(10L, "E1", "GRADE", 0, 1L));
        when(batchMapper.selectById(1L)).thenReturn(batch(1L, LocalDateTime.now().plusDays(1)));

        assertThatThrownBy(() -> service.submitScore("E1", 10L, 80))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.EVAL_SCORE_OUT_OF_RANGE);
        verify(itemMapper, never()).markSubmitted(any(), any(), any());
    }

    @Test
    @DisplayName("提交：数值打分超范围 → 超范围")
    void submit_num_outOfRange() {
        when(itemMapper.selectById(10L)).thenReturn(item(10L, "E1", "NUM", 0, 1L));
        when(batchMapper.selectById(1L)).thenReturn(batch(1L, LocalDateTime.now().plusDays(1)));

        assertThatThrownBy(() -> service.submitScore("E1", 10L, 5))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.EVAL_SCORE_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("提交：非本人明细 → 无权限")
    void submit_notOwner() {
        when(itemMapper.selectById(10L)).thenReturn(item(10L, "OTHER", "NUM", 0, 1L));

        assertThatThrownBy(() -> service.submitScore("E1", 10L, 80))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.EVAL_NO_PERMISSION);
        verify(itemMapper, never()).markSubmitted(any(), any(), any());
    }

    @Test
    @DisplayName("提交：已提交 → 重复")
    void submit_alreadySubmitted() {
        when(itemMapper.selectById(10L)).thenReturn(item(10L, "E1", "NUM", 1, 1L));

        assertThatThrownBy(() -> service.submitScore("E1", 10L, 80))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.EVAL_SCORE_DUPLICATE);
    }

    @Test
    @DisplayName("提交：批次截止已过 → 任务已结束")
    void submit_deadlinePassed() {
        when(itemMapper.selectById(10L)).thenReturn(item(10L, "E1", "NUM", 0, 1L));
        when(batchMapper.selectById(1L)).thenReturn(batch(1L, LocalDateTime.now().minusMinutes(1)));

        assertThatThrownBy(() -> service.submitScore("E1", 10L, 80))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.EVAL_TASK_CLOSED);
    }

    @Test
    @DisplayName("提交：明细不存在 → 明细不存在")
    void submit_itemNotFound() {
        when(itemMapper.selectById(10L)).thenReturn(null);

        assertThatThrownBy(() -> service.submitScore("E1", 10L, 80))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.EVAL_ASSIGN_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("提交：批次为草稿状态 → 批次未激活")
    void submit_batchNotActive() {
        when(itemMapper.selectById(10L)).thenReturn(item(10L, "E1", "NUM", 0, 1L));
        EvalAssignBatch draft = batch(1L, LocalDateTime.now().plusDays(1));
        draft.setStatus(2); // DRAFT
        when(batchMapper.selectById(1L)).thenReturn(draft);

        assertThatThrownBy(() -> service.submitScore("E1", 10L, 80))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.EVAL_BATCH_NOT_ACTIVE);
    }
}
