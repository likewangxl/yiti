package com.bank.branch.platform.performance.service.importer;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.TargetPlanService;
import com.bank.branch.platform.performance.service.TargetValueService;
import com.bank.branch.platform.performance.service.cmd.UpsertTargetValueCmd;
import com.bank.branch.platform.performance.service.importer.impl.TargetImportStrategy;
import com.bank.branch.platform.performance.service.importer.model.TargetImportRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TargetImportStrategy 单元测试（Task P5.2 Red）.
 *
 * <p>用 easyexcel 在内存中生成 xlsx 字节流，构造 MockMultipartFile 驱动策略执行。
 * 目标值 upsert 通过 mock {@link TargetValueService} 观察调用次数/参数。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>10 行全合法 → totalRows=10, successRows=10, failRows=0，upsert 被调 10 次</li>
 *   <li>5 合法 + 3 无效（必填空 / 数值空）→ 记录到 errorSummary，upsert 只被调 5 次</li>
 *   <li>列头不匹配（缺少"目标值"列）→ 抛 {@link PerfErrorCode#IMPORT_COLUMN_MAPPING_INVALID}</li>
 * </ul>
 */
class TargetImportStrategyTest {

    private TargetValueService targetValueService;
    private TargetPlanService targetPlanService;
    private TargetImportStrategy strategy;
    private PerfImportBatch batch;

    @BeforeEach
    void setUp() {
        targetValueService = mock(TargetValueService.class);
        targetPlanService = mock(TargetPlanService.class);
        strategy = new TargetImportStrategy(targetValueService, targetPlanService);

        // 默认：targetPlanCode "PLAN_OK" → planId "P_OK"（测试可按需覆盖）
        PerfTargetPlan plan = new PerfTargetPlan();
        plan.setId("P_OK");
        plan.setPlanCode("PLAN_OK");
        when(targetPlanService.getByCodeOrNull("PLAN_OK")).thenReturn(Optional.of(plan));
        when(targetPlanService.getByCodeOrNull("UNKNOWN_PLAN")).thenReturn(Optional.empty());

        // upsertOne 默认返回 1 (新增)
        when(targetValueService.upsertOne(any(UpsertTargetValueCmd.class))).thenReturn(1);

        batch = new PerfImportBatch();
        batch.setId("BATCH_TEST");
        batch.setBatchNo("IMP20260423001");
        batch.setImportType("TARGET");
        batch.setCreatedBy("admin");
    }

    @Test
    @DisplayName("importType 返回 TARGET")
    void importType_returnsTarget() {
        assertThat(strategy.importType()).isEqualTo("TARGET");
    }

    @Test
    @DisplayName("execute：10 行全合法 → 全部成功，upsert 被调 10 次")
    void execute_allValid_allSuccess() {
        List<TargetImportRow> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rows.add(row("PLAN_OK", "E" + String.format("%03d", i),
                    "TEST_METRIC_TGT", new BigDecimal("100.0000")));
        }
        MultipartFile file = writeExcel(rows);

        ImportResult result = strategy.execute(batch, file, ImportContext.EMPTY);

        assertThat(result).isNotNull();
        assertThat(result.getTotalRows()).isEqualTo(10);
        assertThat(result.getSuccessRows()).isEqualTo(10);
        assertThat(result.getErrorRows()).isEqualTo(0);
        assertThat(result.getErrorSummary()).isNullOrEmpty();

        ArgumentCaptor<UpsertTargetValueCmd> cap = ArgumentCaptor.forClass(UpsertTargetValueCmd.class);
        verify(targetValueService, atLeastOnce()).upsertOne(cap.capture());
        assertThat(cap.getAllValues()).hasSize(10);
        UpsertTargetValueCmd first = cap.getAllValues().get(0);
        assertThat(first.getPlanId()).isEqualTo("P_OK");
        assertThat(first.getSubjectType()).isEqualTo("EMP");
        assertThat(first.getOperator()).isEqualTo("admin");
    }

    @Test
    @DisplayName("execute：5 合法 + 3 无效 → 无效行进 errorSummary，upsert 仅调 5 次")
    void execute_mixedValid_invalidRowsInErrorSummary() {
        List<TargetImportRow> rows = new ArrayList<>();
        // 5 行合法
        for (int i = 0; i < 5; i++) {
            rows.add(row("PLAN_OK", "E" + i, "TEST_METRIC_TGT", new BigDecimal("50.0000")));
        }
        // 3 行无效：分别破坏 empId / metricCode / targetValue
        rows.add(row("PLAN_OK", "", "TEST_METRIC_TGT", new BigDecimal("60.0000"))); // empId 空
        rows.add(row("PLAN_OK", "E999", "", new BigDecimal("70.0000")));             // metricCode 空
        rows.add(row("PLAN_OK", "E888", "TEST_METRIC_TGT", null));                   // targetValue 空

        MultipartFile file = writeExcel(rows);

        ImportResult result = strategy.execute(batch, file, ImportContext.EMPTY);

        assertThat(result.getTotalRows()).isEqualTo(8);
        assertThat(result.getSuccessRows()).isEqualTo(5);
        assertThat(result.getErrorRows()).isEqualTo(3);
        assertThat(result.getErrorSummary())
                .as("errorSummary 含 3 行错误信息")
                .isNotBlank()
                .contains("第")
                .contains("行");
        // 仅 5 行合法走了 upsert
        verify(targetValueService, atLeastOnce()).upsertOne(any());
    }

    @Test
    @DisplayName("execute：列头不匹配（缺目标值列）→ 抛 IMPORT_COLUMN_MAPPING_INVALID (PERF-42203)")
    void execute_columnMismatch_throwsImportColumnMappingInvalid() {
        // 手工拼一个只有 3 列、列头名全不匹配 TargetImportRow 的 Excel
        MultipartFile file = writeExcelWithWrongHead();

        assertThatThrownBy(() -> strategy.execute(batch, file, ImportContext.EMPTY))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.IMPORT_COLUMN_MAPPING_INVALID));
        verify(targetValueService, never()).upsertOne(any());
    }

    @Test
    @DisplayName("execute：targetPlanCode 不存在 → 记录到 errorSummary 不抛异常")
    void execute_unknownPlanCode_recordedInErrorSummary() {
        // setUp 已配置 UNKNOWN_PLAN → Optional.empty()
        List<TargetImportRow> rows = new ArrayList<>();
        rows.add(row("UNKNOWN_PLAN", "E001", "TEST_METRIC_TGT", new BigDecimal("10")));
        MultipartFile file = writeExcel(rows);

        ImportResult result = strategy.execute(batch, file, ImportContext.EMPTY);

        assertThat(result.getTotalRows()).isEqualTo(1);
        assertThat(result.getSuccessRows()).isEqualTo(0);
        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getErrorSummary()).contains("UNKNOWN_PLAN");
        verify(targetValueService, never()).upsertOne(any());
    }

    // =================== helpers ===================

    private static TargetImportRow row(String planCode, String empId, String metricCode, BigDecimal value) {
        TargetImportRow r = new TargetImportRow();
        r.setTargetPlanCode(planCode);
        r.setEmpId(empId);
        r.setMetricCode(metricCode);
        r.setTargetValue(value);
        r.setRemark(null);
        return r;
    }

    private static MultipartFile writeExcel(List<TargetImportRow> rows) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        EasyExcel.write(bos, TargetImportRow.class).sheet("目标导入").doWrite(rows);
        return new MockMultipartFile("file", "targets.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());
    }

    /** 构造一个列头完全不匹配的 Excel（用与 TargetImportRow 无关的类型写入）. */
    private static MultipartFile writeExcelWithWrongHead() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        List<List<String>> head = new ArrayList<>();
        head.add(List.of("Col_A"));
        head.add(List.of("Col_B"));
        head.add(List.of("Col_C"));
        List<List<Object>> data = new ArrayList<>();
        data.add(List.of("x", "y", "z"));
        EasyExcel.write(bos).head(head).sheet("目标导入").doWrite(data);
        return new MockMultipartFile("file", "wrong.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());
    }
}
