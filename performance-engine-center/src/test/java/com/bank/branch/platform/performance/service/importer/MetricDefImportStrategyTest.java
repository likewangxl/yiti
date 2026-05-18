package com.bank.branch.platform.performance.service.importer;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.service.cmd.CreateMetricDefCmd;
import com.bank.branch.platform.performance.service.importer.impl.MetricDefImportStrategy;
import com.bank.branch.platform.performance.service.importer.model.MetricDefImportRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetricDefImportStrategy 单元测试（V1.9 Task M2）.
 *
 * <p>覆盖：
 * <ul>
 *   <li>importType = METRIC_DEF</li>
 *   <li>10 行全合法 → batchCreateMetricDefs 调 1 次，cmd 列表 size=10，且不调度</li>
 *   <li>metric_code 为空 → 自动按 indexNo 生成 M_{:04d}</li>
 *   <li>文件内 metric_code 重复 → 抛 IMPORT_BATCH_ALL_OR_NONE_FAILED + 不调用 service</li>
 *   <li>来源=1 → calc_mode=MANUAL / calc_logic_type=EXPR / exprText=calcRule</li>
 *   <li>来源=2 → calc_mode=AUTO / calc_logic_type=SQL / sqlText=calcRule；空 calcRule → 抛</li>
 *   <li>scheduleType=5 → 抛 METRIC_CALC_FREQ_INVALID（语义对齐，由整批 all-or-none 包装）</li>
 *   <li>statusFlag=2（越界）→ 抛 VALIDATION_FAILED（整批 all-or-none）</li>
 *   <li>DB 已存在 metric_code → 抛整批失败，错误消息含已有编号</li>
 * </ul>
 */
class MetricDefImportStrategyTest {

    private MetricDefService metricDefService;
    private MetricDefImportStrategy strategy;
    private PerfImportBatch batch;

    @BeforeEach
    void setUp() {
        metricDefService = mock(MetricDefService.class);
        strategy = new MetricDefImportStrategy(metricDefService);

        // 默认 DB 不存在任何 metric_code
        when(metricDefService.getByCodes(anyList())).thenReturn(Collections.emptyList());
        // batchCreateMetricDefs 默认返回空列表（被 ArgumentCaptor 校验，不读返回值）
        when(metricDefService.batchCreateMetricDefs(anyList(), anyString()))
                .thenReturn(Collections.emptyList());

        batch = new PerfImportBatch();
        batch.setId("BATCH_M");
        batch.setBatchNo("IMP20260517001");
        batch.setImportType("METRIC_DEF");
        batch.setCreatedBy("admin");
    }

    @Test
    @DisplayName("importType 返回 METRIC_DEF")
    void importType_returnsMetricDef() {
        assertThat(strategy.importType()).isEqualTo("METRIC_DEF");
    }

    @Test
    @DisplayName("10 行全合法 → batchCreateMetricDefs 调用 1 次，cmds.size=10")
    void execute_allValid_callBatchOnce() {
        List<MetricDefImportRow> rows = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            rows.add(row(i, 1, "指标" + i, "M_C" + i, "规模类", 2, "select 1", 1, 1));
        }
        MultipartFile file = writeExcel(rows);

        ImportResult result = strategy.execute(batch, file);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateMetricDefCmd>> captor = ArgumentCaptor.forClass(List.class);
        verify(metricDefService, times(1)).batchCreateMetricDefs(captor.capture(), anyString());
        assertThat(captor.getValue()).hasSize(10);
        assertThat(result.getTotalRows()).isEqualTo(10);
        assertThat(result.getSuccessRows()).isEqualTo(10);
        assertThat(result.getErrorRows()).isZero();
    }

    @Test
    @DisplayName("metric_code 空 → 按 M_{indexNo:04d} 自动生成")
    void execute_emptyMetricCode_generates() {
        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "存款余额", null, "规模类", 2, "select 1", 1, 1),
                row(42, 2, "存款日均", "", "规模类", 2, "select 2", 2, 1));
        MultipartFile file = writeExcel(rows);

        strategy.execute(batch, file);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateMetricDefCmd>> captor = ArgumentCaptor.forClass(List.class);
        verify(metricDefService).batchCreateMetricDefs(captor.capture(), anyString());
        List<CreateMetricDefCmd> cmds = captor.getValue();
        assertThat(cmds.get(0).getMetricCode()).isEqualTo("M_0001");
        assertThat(cmds.get(1).getMetricCode()).isEqualTo("M_0042");
    }

    @Test
    @DisplayName("文件内 metric_code 重复 → 整批失败 + 不调用 service")
    void execute_duplicateMetricCodeInFile_throwsAllOrNone() {
        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "A", "M_DUP", "规模类", 2, "select 1", 1, 1),
                row(2, 1, "B", "M_DUP", "规模类", 2, "select 2", 1, 1));
        MultipartFile file = writeExcel(rows);

        assertThatThrownBy(() -> strategy.execute(batch, file))
                .isInstanceOf(PerfException.class)
                .satisfies(e -> assertThat(((PerfException) e).getErrorCode())
                        .isEqualTo(PerfErrorCode.IMPORT_BATCH_ALL_OR_NONE_FAILED))
                .hasMessageContaining("M_DUP")
                .hasMessageContaining("第3行")  // Excel 物理行号（第 2 行 fixture = Excel 第 3 行）
                .hasMessageContaining("第2行"); // 首次出现的行号

        verify(metricDefService, never()).batchCreateMetricDefs(anyList(), anyString());
    }

    @Test
    @DisplayName("来源=1 → calc_mode=MANUAL / calc_logic_type=EXPR / exprText=calcRule，无需 sql")
    void execute_source1_manualExpr() {
        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "外部导入指标", "M_EXT", "规模类", 1, "外部填值即可", 1, 1));
        MultipartFile file = writeExcel(rows);

        strategy.execute(batch, file);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateMetricDefCmd>> captor = ArgumentCaptor.forClass(List.class);
        verify(metricDefService).batchCreateMetricDefs(captor.capture(), anyString());
        CreateMetricDefCmd cmd = captor.getValue().get(0);
        assertThat(cmd.getCalcMode()).isEqualTo("MANUAL");
        assertThat(cmd.getCalcLogicType()).isEqualTo("EXPR");
        assertThat(cmd.getExprText()).isEqualTo("外部填值即可");
        assertThat(cmd.getSqlText()).isNull();
    }

    @Test
    @DisplayName("来源=2 → calc_mode=AUTO / calc_logic_type=SQL / sqlText=calcRule")
    void execute_source2_autoSql() {
        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "系统提取", "M_SYS", "效益类", 2, "select sum(x) from t", 2, 1));
        MultipartFile file = writeExcel(rows);

        strategy.execute(batch, file);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateMetricDefCmd>> captor = ArgumentCaptor.forClass(List.class);
        verify(metricDefService).batchCreateMetricDefs(captor.capture(), anyString());
        CreateMetricDefCmd cmd = captor.getValue().get(0);
        assertThat(cmd.getCalcMode()).isEqualTo("AUTO");
        assertThat(cmd.getCalcLogicType()).isEqualTo("SQL");
        assertThat(cmd.getSqlText()).isEqualTo("select sum(x) from t");
        assertThat(cmd.getCalcFreq()).isEqualTo("MONTH");
        assertThat(cmd.getMetricCategory()).isEqualTo("效益类");
    }

    @Test
    @DisplayName("V1.9：statusFlag=1 → cmd.status=ACTIVE，落库尊重 Excel 意图")
    void execute_statusFlag1_setsActive() {
        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "启用指标", "M_S_ON", "规模类", 2, "select 1", 1, 1));
        MultipartFile file = writeExcel(rows);

        strategy.execute(batch, file);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateMetricDefCmd>> captor = ArgumentCaptor.forClass(List.class);
        verify(metricDefService).batchCreateMetricDefs(captor.capture(), anyString());
        assertThat(captor.getValue().get(0).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("V1.9：statusFlag=0 → cmd.status=DISABLED，导入即停用")
    void execute_statusFlag0_setsDisabled() {
        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "停用指标", "M_S_OFF", "规模类", 2, "select 1", 1, 0));
        MultipartFile file = writeExcel(rows);

        strategy.execute(batch, file);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateMetricDefCmd>> captor = ArgumentCaptor.forClass(List.class);
        verify(metricDefService).batchCreateMetricDefs(captor.capture(), anyString());
        assertThat(captor.getValue().get(0).getStatus()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("V1.9：导入指标 baseDim 默认 null（维度无关型指标）")
    void execute_baseDim_defaultsToNull() {
        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "通用指标", "M_META", "规模类", 2, "select 1", 1, 1));
        MultipartFile file = writeExcel(rows);

        strategy.execute(batch, file);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateMetricDefCmd>> captor = ArgumentCaptor.forClass(List.class);
        verify(metricDefService).batchCreateMetricDefs(captor.capture(), anyString());
        CreateMetricDefCmd cmd = captor.getValue().get(0);
        // V1.9：Excel 模板无 base_dim 列，所有导入指标 baseDim=null，不占 slot
        assertThat(cmd.getBaseDim()).isNull();
    }

    @Test
    @DisplayName("来源=2 但 calcRule 空 → 整批失败")
    void execute_source2_emptyCalcRule_throws() {
        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "缺SQL", "M_NOSQL", "规模类", 2, "", 1, 1));
        MultipartFile file = writeExcel(rows);

        assertThatThrownBy(() -> strategy.execute(batch, file))
                .isInstanceOf(PerfException.class)
                .satisfies(e -> assertThat(((PerfException) e).getErrorCode())
                        .isEqualTo(PerfErrorCode.IMPORT_BATCH_ALL_OR_NONE_FAILED))
                .hasMessageContaining("第2行");
        verify(metricDefService, never()).batchCreateMetricDefs(anyList(), anyString());
    }

    @Test
    @DisplayName("scheduleType=5 越界 → 整批失败")
    void execute_invalidSchedule_throws() {
        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "X", "M_X", "规模类", 2, "select 1", 5, 1));
        MultipartFile file = writeExcel(rows);

        assertThatThrownBy(() -> strategy.execute(batch, file))
                .isInstanceOf(PerfException.class)
                .satisfies(e -> assertThat(((PerfException) e).getErrorCode())
                        .isEqualTo(PerfErrorCode.IMPORT_BATCH_ALL_OR_NONE_FAILED));
        verify(metricDefService, never()).batchCreateMetricDefs(anyList(), anyString());
    }

    @Test
    @DisplayName("statusFlag=2 越界 → 整批失败")
    void execute_invalidStatus_throws() {
        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "X", "M_X", "规模类", 2, "select 1", 1, 2));
        MultipartFile file = writeExcel(rows);

        assertThatThrownBy(() -> strategy.execute(batch, file))
                .isInstanceOf(PerfException.class)
                .satisfies(e -> assertThat(((PerfException) e).getErrorCode())
                        .isEqualTo(PerfErrorCode.IMPORT_BATCH_ALL_OR_NONE_FAILED));
    }

    @Test
    @DisplayName("DB 已存在 metric_code → 整批失败 + 错误消息含编号")
    void execute_dbCodeExists_throws() {
        // 模拟 DB 返回已存在
        PerfMetricDef existing = new PerfMetricDef();
        existing.setMetricCode("M_OLD");
        when(metricDefService.getByCodes(anyList())).thenReturn(List.of(existing));

        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "已有", "M_OLD", "规模类", 2, "select 1", 1, 1));
        MultipartFile file = writeExcel(rows);

        assertThatThrownBy(() -> strategy.execute(batch, file))
                .isInstanceOf(PerfException.class)
                .satisfies(e -> assertThat(((PerfException) e).getErrorCode())
                        .isEqualTo(PerfErrorCode.IMPORT_BATCH_ALL_OR_NONE_FAILED))
                .hasMessageContaining("M_OLD");
        verify(metricDefService, never()).batchCreateMetricDefs(anyList(), anyString());
    }

    // ===== fixture helpers =====

    private static MetricDefImportRow row(Integer indexNo, Integer level, String name, String code,
                                          String category, Integer source, String rule,
                                          Integer schedule, Integer status) {
        MetricDefImportRow r = new MetricDefImportRow();
        r.setIndexNo(indexNo);
        r.setMetricLevel(level);
        r.setMetricName(name);
        r.setMetricCode(code);
        r.setMetricCategory(category);
        r.setSourceType(source);
        r.setCalcRule(rule);
        r.setScheduleType(schedule);
        r.setStatusFlag(status);
        return r;
    }

    /**
     * 以 MetricDefImportRow 列表生成 xlsx 字节流（列头由 @ExcelProperty 反推）.
     */
    private static MultipartFile writeExcel(List<MetricDefImportRow> rows) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        EasyExcel.write(baos, MetricDefImportRow.class).sheet("Sheet1").doWrite(rows);
        return new MockMultipartFile("file", "metric-def.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                baos.toByteArray());
    }
}
