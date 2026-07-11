package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.performance.eval.dto.EvalRewardImportRow;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalRewardItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalRewardItemMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EvalRewardImportService} 单元测试（奖励分配异步导入）。
 * <p>覆盖 parseRows / createImportingBatch(REWARD) / processImport 成功·行错误·跨行合计一致性。</p>
 */
@ExtendWith(MockitoExtension.class)
class EvalRewardImportServiceTest {

    @Mock UserApi userApi;
    @Mock EvalAssignBatchMapper batchMapper;
    @Mock EvalRewardItemMapper itemMapper;
    EvalRewardImportService service;

    private final LocalDateTime deadline = LocalDateTime.now().plusDays(7);

    private EvalRewardImportRow row(String beId, String beName, String dept,
                                    String original, String cash, String assignUser, String total) {
        EvalRewardImportRow r = new EvalRewardImportRow();
        r.setBeAssignedUserId(beId);
        r.setBeAssignedUserName(beName);
        r.setDeptName(dept);
        r.setOriginalValue(original == null ? null : new BigDecimal(original));
        r.setCashValue(cash == null ? null : new BigDecimal(cash));
        r.setAssignUserId(assignUser);
        r.setAssignTotal(total == null ? null : new BigDecimal(total));
        return r;
    }

    private EvalAssignBatch importingBatch(long id) {
        EvalAssignBatch b = new EvalAssignBatch();
        b.setBatchId(id);
        b.setStatus(3);
        b.setSource("IMPORT");
        b.setCreateTime(LocalDateTime.now());
        return b;
    }

    @BeforeEach
    void setUp() {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        service = new EvalRewardImportService(userApi, batchMapper, itemMapper, new ObjectMapper(), tm);
    }

    private void mockAssignersExist(String... usernames) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        for (String n : usernames) map.put(n, "ID_" + n);
        when(userApi.mapUsernamesToEmpId(anyList())).thenReturn(map);
    }

    private EvalAssignBatch captureUpdatedBatch() {
        ArgumentCaptor<EvalAssignBatch> cap = ArgumentCaptor.forClass(EvalAssignBatch.class);
        verify(batchMapper).updateById(cap.capture());
        return cap.getValue();
    }

    @Test
    @DisplayName("parseRows：空文件 → EVAL_IMPORT_FILE_EMPTY")
    void parseRows_empty_throws() {
        MultipartFile empty = new MockMultipartFile("file", "t.xlsx", null, new byte[0]);
        assertThatThrownBy(() -> service.parseRows(empty))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("导入文件为空");
    }

    @Test
    @DisplayName("createImportingBatch：建 STATUS=3 REWARD 批次")
    void createImportingBatch_status3Reward() {
        when(batchMapper.insert(any(EvalAssignBatch.class))).thenAnswer(inv -> {
            ((EvalAssignBatch) inv.getArgument(0)).setBatchId(77L);
            return 1;
        });
        Long id = service.createImportingBatch("奖励任务", deadline, "ADMIN");
        assertThat(id).isEqualTo(77L);
        ArgumentCaptor<EvalAssignBatch> cap = ArgumentCaptor.forClass(EvalAssignBatch.class);
        verify(batchMapper).insert(cap.capture());
        assertThat(cap.getValue().getTaskType()).isEqualTo("REWARD");
        assertThat(cap.getValue().getStatus()).isEqualTo(3);
        assertThat(cap.getValue().getSource()).isEqualTo("IMPORT");
        assertThat(cap.getValue().getBatchName()).isEqualTo("奖励任务");
    }

    @Test
    @DisplayName("全部合法 → 批量插明细 + 批次 STATUS=2；分配人归一 USER_ID，被分配人原样")
    void processImport_allValid_persistsDraft() {
        mockAssignersExist("A1");
        when(batchMapper.selectById(77L)).thenReturn(importingBatch(77L));
        List<EvalRewardImportRow> rows = List.of(
                row("B1", "被一", "信贷部", "76.5", "80", "A1", "100"),
                row("B2", "被二", "信贷部", "60", "65", "A1", "100"));

        service.processImport(77L, rows, "奖励任务", deadline, "ADMIN");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EvalRewardItem>> cap = ArgumentCaptor.forClass(List.class);
        verify(itemMapper).batchInsert(cap.capture());
        List<EvalRewardItem> items = cap.getValue();
        assertThat(items).hasSize(2);
        assertThat(items).allMatch(i -> i.getAssignUserId().equals("ID_A1"));
        assertThat(items.stream().map(EvalRewardItem::getBeAssignedUserId).collect(Collectors.toList()))
                .containsExactly("B1", "B2");
        assertThat(items.get(0).getAssignTotal()).isEqualByComparingTo("100");
        assertThat(items.get(0).getSubmitted()).isEqualTo(0);
        assertThat(items.get(0).getAssignValue()).isNull();
        EvalAssignBatch b = captureUpdatedBatch();
        assertThat(b.getStatus()).isEqualTo(2);
        assertThat(b.getImportedCount()).isEqualTo(2);
        assertThat(b.getTotalRows()).isEqualTo(2);
    }

    @Test
    @DisplayName("行错误：分配人工号不存在 → STATUS=4 + errorSummary 含『分配人工号不存在』")
    void processImport_assignerNotExist_fails() {
        mockAssignersExist("A1"); // A2 不存在
        when(batchMapper.selectById(77L)).thenReturn(importingBatch(77L));
        service.processImport(77L, List.of(row("B1", "被一", "信贷部", "10", "10", "A2", "100")),
                "奖励任务", deadline, "ADMIN");
        verify(itemMapper, never()).batchInsert(anyList());
        EvalAssignBatch b = captureUpdatedBatch();
        assertThat(b.getStatus()).isEqualTo(4);
        assertThat(b.getErrorSummary()).contains("分配人工号不存在");
    }

    @Test
    @DisplayName("行错误：分配合计<=0 → errorSummary 含『分配合计』")
    void processImport_totalNotPositive_fails() {
        mockAssignersExist("A1");
        when(batchMapper.selectById(77L)).thenReturn(importingBatch(77L));
        service.processImport(77L, List.of(row("B1", "被一", "信贷部", "10", "10", "A1", "0")),
                "奖励任务", deadline, "ADMIN");
        assertThat(captureUpdatedBatch().getErrorSummary()).contains("分配合计");
    }

    @Test
    @DisplayName("跨行：同(分配人+部门)组 分配合计不一致 → errorSummary 含『分配合计不一致』")
    void processImport_totalInconsistentWithinGroup_fails() {
        mockAssignersExist("A1");
        when(batchMapper.selectById(77L)).thenReturn(importingBatch(77L));
        service.processImport(77L, List.of(
                row("B1", "被一", "信贷部", "10", "10", "A1", "100"),
                row("B2", "被二", "信贷部", "10", "10", "A1", "200")), // 同组不同合计
                "奖励任务", deadline, "ADMIN");
        verify(itemMapper, never()).batchInsert(anyList());
        assertThat(captureUpdatedBatch().getErrorSummary()).contains("分配合计不一致");
    }

    @Test
    @DisplayName("行错误：被分配人工号为空 → errorSummary 含『被分配人工号』")
    void processImport_emptyBeAssigned_fails() {
        mockAssignersExist("A1");
        when(batchMapper.selectById(77L)).thenReturn(importingBatch(77L));
        service.processImport(77L, List.of(row("", "被一", "信贷部", "10", "10", "A1", "100")),
                "奖励任务", deadline, "ADMIN");
        assertThat(captureUpdatedBatch().getErrorSummary()).contains("被分配人工号");
    }

    @Test
    @DisplayName("行错误：分配合计为空(无法解析) → errorSummary 含『分配合计』")
    void processImport_totalNull_fails() {
        mockAssignersExist("A1");
        when(batchMapper.selectById(77L)).thenReturn(importingBatch(77L));
        service.processImport(77L, List.of(row("B1", "被一", "信贷部", "10", "10", "A1", null)),
                "奖励任务", deadline, "ADMIN");
        assertThat(captureUpdatedBatch().getErrorSummary()).contains("分配合计");
    }

    @Test
    @DisplayName("不同部门同分配人 → 各组独立合计，均合法可入库")
    void processImport_differentDeptsIndependent() {
        mockAssignersExist("A1");
        when(batchMapper.selectById(77L)).thenReturn(importingBatch(77L));
        service.processImport(77L, List.of(
                row("B1", "被一", "信贷部", "10", "10", "A1", "100"),
                row("B2", "被二", "零售部", "10", "10", "A1", "200")), // 不同部门不同合计合法
                "奖励任务", deadline, "ADMIN");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EvalRewardItem>> cap = ArgumentCaptor.forClass(List.class);
        verify(itemMapper).batchInsert(cap.capture());
        assertThat(cap.getValue()).hasSize(2);
    }
}
