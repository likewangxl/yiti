package com.bank.branch.platform.performance.eval.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.dto.DictItemDTO;
import com.bank.branch.platform.performance.eval.dto.EvalAssignImportRow;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalAssignItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignItemMapper;
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

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EvalAssignImportService} 单元测试（异步化重写）。
 *
 * <p>覆盖新公开面：{@code parseRows}（同步解析、空文件快速失败）、{@code createImportingBatch}
 * （建 IMPORTING(3) 批次）、{@code processImport}（异步逐行校验入库的成功/行错误/真异常三态），
 * 并保留全部既有 all-or-none 校验规则与错误码（通过 processImport 失败路径的 errorSummary 断言）。</p>
 */
@ExtendWith(MockitoExtension.class)
class EvalAssignImportServiceTest {

    @Mock UserApi userApi;
    @Mock DictApi dictApi;
    @Mock EvalAssignBatchMapper batchMapper;
    @Mock EvalAssignItemMapper itemMapper;
    EvalAssignImportService service;

    private final LocalDateTime deadline = LocalDateTime.now().plusDays(7);

    private DictItemDTO dict(String type, String code, String label) {
        DictItemDTO d = new DictItemDTO();
        d.setDictType(type);
        d.setDictCode(code);
        d.setDictLabel(label);
        d.setDictValue(code);
        return d;
    }

    /** 10 列 helper。 */
    private EvalAssignImportRow row(String beId, String beName, String beDept, String beTag,
                                    String evId, String evName, String evTag, String evDept,
                                    String weight, String scoreType) {
        EvalAssignImportRow r = new EvalAssignImportRow();
        r.setBeEvalUserId(beId);
        r.setBeEvalUserName(beName);
        r.setBeEvalDept(beDept);
        r.setBeEvalTag(beTag);
        r.setEvalUserId(evId);
        r.setEvalUserName(evName);
        r.setEvalUserTag(evTag);
        r.setEvalUserDept(evDept);
        r.setWeightTag(weight);
        r.setScoreTypeText(scoreType);
        return r;
    }

    /** 返回一个 IMPORTING(3) 批次（模拟 createImportingBatch 已落库）。 */
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
        service = new EvalAssignImportService(userApi, dictApi, batchMapper, itemMapper, new ObjectMapper(), tm);
        lenient().when(dictApi.getDictItems("EVAL_WEIGHT_TAG")).thenReturn(List.of(
                dict("EVAL_WEIGHT_TAG", "MAIN", "主要"),
                dict("EVAL_WEIGHT_TAG", "MINOR", "次要")));
        lenient().when(dictApi.getDictItems("EVAL_SCORE_TYPE")).thenReturn(List.of(
                dict("EVAL_SCORE_TYPE", "NUM", "数值打分"),
                dict("EVAL_SCORE_TYPE", "GRADE", "等级打分")));
    }

    /** 入参为「用户名」；每个用户名映射到 USER_ID = "ID_" + 用户名（轻量批量 mapUsernamesToEmpId）。 */
    private void mockUsersExist(String... usernames) {
        java.util.Map<String, String> nameToUserId = new java.util.HashMap<>();
        for (String name : usernames) {
            nameToUserId.put(name, "ID_" + name);
        }
        when(userApi.mapUsernamesToEmpId(anyList())).thenReturn(nameToUserId);
    }

    /** 捕获 processImport 失败/成功时对批次的 updateById 入参。 */
    private EvalAssignBatch captureUpdatedBatch() {
        ArgumentCaptor<EvalAssignBatch> cap = ArgumentCaptor.forClass(EvalAssignBatch.class);
        verify(batchMapper).updateById(cap.capture());
        return cap.getValue();
    }

    // ================== parseRows ==================

    @Test
    @DisplayName("parseRows：文件为空 → 抛 EVAL_IMPORT_FILE_EMPTY（接口线程快速失败）")
    void parseRows_emptyFile_throws() {
        MultipartFile empty = new MockMultipartFile("file", "t.xlsx", null, new byte[0]);
        assertThatThrownBy(() -> service.parseRows(empty))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("导入文件为空");
    }

    @Test
    @DisplayName("parseRows：合法 xlsx → 解析出行集")
    void parseRows_validFile_returnsRows() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        EvalAssignImportRow sample = row("B1", "被一", "信贷部", "客户经理",
                "E1", "评一", "支行长", "管理部", "主要", "数值打分");
        EasyExcel.write(bos, EvalAssignImportRow.class).sheet("评价任务").doWrite(List.of(sample));
        MultipartFile file = new MockMultipartFile("file", "t.xlsx", null, bos.toByteArray());

        List<EvalAssignImportRow> rows = service.parseRows(file);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getBeEvalUserId()).isEqualTo("B1");
        assertThat(rows.get(0).getScoreTypeText()).isEqualTo("数值打分");
    }

    // ================== createImportingBatch ==================

    @Test
    @DisplayName("createImportingBatch：建 STATUS=3 IMPORTING 批次并回填 batchId")
    void createImportingBatch_insertsStatus3() {
        when(batchMapper.insert(any(EvalAssignBatch.class))).thenAnswer(inv -> {
            ((EvalAssignBatch) inv.getArgument(0)).setBatchId(99L);
            return 1;
        });

        Long batchId = service.createImportingBatch("EVAL", "测试任务", deadline, "ADMIN");

        assertThat(batchId).isEqualTo(99L);
        ArgumentCaptor<EvalAssignBatch> cap = ArgumentCaptor.forClass(EvalAssignBatch.class);
        verify(batchMapper).insert(cap.capture());
        EvalAssignBatch b = cap.getValue();
        assertThat(b.getStatus()).isEqualTo(3);
        assertThat(b.getSource()).isEqualTo("IMPORT");
        assertThat(b.getTaskType()).isEqualTo("EVAL");
        assertThat(b.getBatchName()).isEqualTo("测试任务");
        assertThat(b.getDeadline()).isEqualTo(deadline);
        assertThat(b.getCreateBy()).isEqualTo("ADMIN");
    }

    // ================== processImport 成功 ==================

    @Test
    @DisplayName("processImport 全部合法 → 批量插明细 + 批次 STATUS=2 + importedCount/totalRows，评价类型映射为编码")
    void processImport_allValid_persistsAndDraft() {
        mockUsersExist("B1", "B2", "E1", "E2");
        when(batchMapper.selectById(99L)).thenReturn(importingBatch(99L));
        List<EvalAssignImportRow> rows = List.of(
                row("B1", "被一", "信贷部", "客户经理", "E1", "评一", "支行长", "管理部", "主要", "数值打分"),
                row("B2", "被二", "信贷部", "客户经理", "E2", "评二", "支行长", "管理部", "次要", "等级打分"));

        service.processImport(99L, rows, "EVAL", "测试任务", deadline, "ADMIN");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EvalAssignItem>> itemCap = ArgumentCaptor.forClass(List.class);
        verify(itemMapper).batchInsert(itemCap.capture());
        List<EvalAssignItem> items = itemCap.getValue();
        assertThat(items).hasSize(2);
        assertThat(items).allMatch(i -> i.getBatchId().equals(99L));
        assertThat(items.stream().map(EvalAssignItem::getScoreType).collect(Collectors.toList()))
                .containsExactly("NUM", "GRADE");
        assertThat(items.get(0).getWeightTag()).isEqualTo("主要");
        assertThat(items.stream().map(EvalAssignItem::getBeEvalUserId).collect(Collectors.toList()))
                .containsExactly("ID_B1", "ID_B2");
        assertThat(items.stream().map(EvalAssignItem::getEvalUserId).collect(Collectors.toList()))
                .containsExactly("ID_E1", "ID_E2");

        EvalAssignBatch updated = captureUpdatedBatch();
        assertThat(updated.getStatus()).isEqualTo(2);
        assertThat(updated.getImportedCount()).isEqualTo(2);
        assertThat(updated.getTotalRows()).isEqualTo(2);
        assertThat(updated.getErrorSummary()).isNull();
    }

    @Test
    @DisplayName("processImport 明细超单批上限 → 分多次 batchInsert，每批不超过上限")
    void processImport_overBatchSize_splitsIntoChunks() {
        service.setBatchInsertSize(2);
        mockUsersExist("B1", "B2", "B3", "B4", "B5", "E1");
        when(batchMapper.selectById(99L)).thenReturn(importingBatch(99L));
        List<EvalAssignImportRow> rows = List.of(
                row("B1", "被一", "信贷部", "t", "E1", "评一", "t", "d", "主要", "数值打分"),
                row("B2", "被二", "信贷部", "t", "E1", "评一", "t", "d", "主要", "数值打分"),
                row("B3", "被三", "信贷部", "t", "E1", "评一", "t", "d", "主要", "数值打分"),
                row("B4", "被四", "信贷部", "t", "E1", "评一", "t", "d", "主要", "数值打分"),
                row("B5", "被五", "信贷部", "t", "E1", "评一", "t", "d", "主要", "数值打分"));

        service.processImport(99L, rows, "EVAL", "测试任务", deadline, "ADMIN");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EvalAssignItem>> chunkCap = ArgumentCaptor.forClass(List.class);
        verify(itemMapper, times(3)).batchInsert(chunkCap.capture());
        assertThat(chunkCap.getAllValues().stream().map(List::size).collect(Collectors.toList()))
                .containsExactly(2, 2, 1);
        assertThat(captureUpdatedBatch().getImportedCount()).isEqualTo(5);
    }

    // ================== processImport 行错误 → STATUS=4 ==================

    @Test
    @DisplayName("processImport 任一行错误 → 不插明细 + 批次 STATUS=4 + errorSummary 非空")
    void processImport_anyError_failsWithSummary() {
        mockUsersExist("B1", "E1"); // B2 不存在
        when(batchMapper.selectById(99L)).thenReturn(importingBatch(99L));
        List<EvalAssignImportRow> rows = List.of(
                row("B1", "被一", "信贷部", "t", "E1", "评一", "t", "d", "主要", "数值打分"),
                row("B2", "被二", "信贷部", "t", "E1", "评一", "t", "d", "主要", "数值打分"));

        service.processImport(99L, rows, "EVAL", "测试任务", deadline, "ADMIN");

        verify(itemMapper, never()).batchInsert(anyList());
        EvalAssignBatch updated = captureUpdatedBatch();
        assertThat(updated.getStatus()).isEqualTo(4);
        assertThat(updated.getTotalRows()).isEqualTo(2);
        assertThat(updated.getImportedCount()).isNull();
        assertThat(updated.getErrorSummary())
                .isNotEmpty()
                .contains("被打分员工用户名不存在");
    }

    @Test
    @DisplayName("行错误：员工编号填 USER_ID 而非用户名 → errorSummary 含『被打分员工用户名不存在』")
    void processImport_userIdInsteadOfUsername_failSummary() {
        when(userApi.mapUsernamesToEmpId(anyList())).thenReturn(java.util.Map.of("N1", "ID_N1"));
        when(batchMapper.selectById(99L)).thenReturn(importingBatch(99L));
        service.processImport(99L, List.of(
                row("ID_N1", "被一", "信贷部", "t", "N1", "评一", "t", "d", "主要", "数值打分")),
                "EVAL", "测试任务", deadline, "ADMIN");
        verify(itemMapper, never()).batchInsert(anyList());
        assertThat(captureUpdatedBatch().getErrorSummary()).contains("被打分员工用户名不存在");
    }

    @Test
    @DisplayName("行错误：打分人用户名不存在 → errorSummary 含『打分员工用户名不存在』")
    void processImport_scorerNotExist_failSummary() {
        mockUsersExist("B1"); // E1 不存在
        when(batchMapper.selectById(99L)).thenReturn(importingBatch(99L));
        service.processImport(99L, List.of(
                row("B1", "被一", "信贷部", "t", "E1", "评一", "t", "d", "主要", "数值打分")),
                "EVAL", "测试任务", deadline, "ADMIN");
        assertThat(captureUpdatedBatch().getErrorSummary()).contains("打分员工用户名不存在");
    }

    @Test
    @DisplayName("行错误：权重标签不在字典 → errorSummary 含『权重标签』")
    void processImport_weightTagInvalid_failSummary() {
        mockUsersExist("B1", "E1");
        when(batchMapper.selectById(99L)).thenReturn(importingBatch(99L));
        service.processImport(99L, List.of(
                row("B1", "被一", "信贷部", "t", "E1", "评一", "t", "d", "不存在权重", "数值打分")),
                "EVAL", "测试任务", deadline, "ADMIN");
        assertThat(captureUpdatedBatch().getErrorSummary()).contains("权重标签");
    }

    @Test
    @DisplayName("行错误：评价类型不在字典 → errorSummary 含『评价类型』")
    void processImport_scoreTypeInvalid_failSummary() {
        mockUsersExist("B1", "E1");
        when(batchMapper.selectById(99L)).thenReturn(importingBatch(99L));
        service.processImport(99L, List.of(
                row("B1", "被一", "信贷部", "t", "E1", "评一", "t", "d", "主要", "星级")),
                "EVAL", "测试任务", deadline, "ADMIN");
        assertThat(captureUpdatedBatch().getErrorSummary()).contains("评价类型");
    }

    @Test
    @DisplayName("行错误：文件内配对重复 → errorSummary 含『重复』")
    void processImport_duplicatePair_failSummary() {
        mockUsersExist("B1", "E1");
        when(batchMapper.selectById(99L)).thenReturn(importingBatch(99L));
        service.processImport(99L, List.of(
                row("B1", "被一", "信贷部", "t", "E1", "评一", "t", "d", "主要", "数值打分"),
                row("B1", "被一", "信贷部", "t", "E1", "评一", "t", "d", "次要", "等级打分")),
                "EVAL", "测试任务", deadline, "ADMIN");
        assertThat(captureUpdatedBatch().getErrorSummary()).contains("重复");
    }

    @Test
    @DisplayName("行错误：编号为空 → errorSummary 含『被打分员工编号』")
    void processImport_emptyId_failSummary() {
        when(batchMapper.selectById(99L)).thenReturn(importingBatch(99L));
        service.processImport(99L, List.of(
                row("", "被一", "信贷部", "t", "E1", "评一", "t", "d", "主要", "数值打分")),
                "EVAL", "测试任务", deadline, "ADMIN");
        verify(itemMapper, never()).batchInsert(anyList());
        assertThat(captureUpdatedBatch().getErrorSummary()).contains("被打分员工编号");
    }

    @Test
    @DisplayName("errorSummary 封顶：errorKeep=2，3 条错误 → 仅保留前 2 条，total=3")
    void processImport_errorSummaryCapped() {
        service.setErrorKeep(2);
        // 3 行均缺被打分人编号 → 3 条同类错误
        when(batchMapper.selectById(99L)).thenReturn(importingBatch(99L));
        service.processImport(99L, List.of(
                row("", "x", "d", "t", "E1", "e", "t", "d", "主要", "数值打分"),
                row("", "y", "d", "t", "E2", "e", "t", "d", "主要", "数值打分"),
                row("", "z", "d", "t", "E3", "e", "t", "d", "主要", "数值打分")),
                "EVAL", "测试任务", deadline, "ADMIN");
        String summary = captureUpdatedBatch().getErrorSummary();
        assertThat(summary).contains("\"total\":3");
        // 仅前 2 条明细：第三行不应出现（errors 数组长度=2）
        assertThat(summary.split("\"row\"", -1).length - 1).isEqualTo(2);
    }

    // ================== processImport 真异常 → STATUS=4 ==================

    @Test
    @DisplayName("processImport 入库真异常 → 批次 STATUS=4（异常摘要），不向上抛出")
    void processImport_persistThrows_failsStatus4() {
        mockUsersExist("B1", "E1");
        when(batchMapper.selectById(99L)).thenReturn(importingBatch(99L));
        // 全部校验通过，但批量插入抛异常（DB 故障）
        when(itemMapper.batchInsert(anyList())).thenThrow(new RuntimeException("DB down"));

        service.processImport(99L, List.of(
                row("B1", "被一", "信贷部", "t", "E1", "评一", "t", "d", "主要", "数值打分")),
                "EVAL", "测试任务", deadline, "ADMIN");

        EvalAssignBatch updated = captureUpdatedBatch();
        assertThat(updated.getStatus()).isEqualTo(4);
        assertThat(updated.getErrorSummary()).contains("导入异常");
    }
}
