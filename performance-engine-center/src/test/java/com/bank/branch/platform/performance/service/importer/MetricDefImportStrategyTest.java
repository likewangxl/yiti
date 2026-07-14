package com.bank.branch.platform.performance.service.importer;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.service.cmd.CreateMetricDefCmd;
import com.bank.branch.platform.performance.service.importer.impl.MetricDefImportStrategy;
import com.bank.branch.platform.performance.service.importer.model.MetricDefImportRow;
import com.bank.branch.platform.performance.service.result.BatchUpsertMetricDefResult;
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
 *   <li>10 行全合法 → batchUpsertByName 调 1 次，cmd 列表 size=10，且不调度</li>
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

        // V1.11：默认 batchUpsertByName 返回全新增（按入参 size 推导）
        when(metricDefService.batchUpsertByName(anyList(), anyString()))
                .thenAnswer(inv -> {
                    java.util.List<?> cmds = inv.getArgument(0);
                    int size = cmds == null ? 0 : cmds.size();
                    return new BatchUpsertMetricDefResult(size, 0, Collections.emptyList());
                });

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
    @DisplayName("10 行全合法 → batchUpsertByName 调用 1 次，cmds.size=10")
    void execute_allValid_callBatchOnce() {
        List<MetricDefImportRow> rows = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            rows.add(row(i, 1, "指标" + i, "M_C" + i, "规模类", "AUTO", "select 1", "ACTIVE"));
        }
        MultipartFile file = writeExcel(rows);

        ImportResult result = strategy.execute(batch, file, ImportContext.EMPTY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateMetricDefCmd>> captor = ArgumentCaptor.forClass(List.class);
        verify(metricDefService, times(1)).batchUpsertByName(captor.capture(), anyString());
        assertThat(captor.getValue()).hasSize(10);
        assertThat(result.getTotalRows()).isEqualTo(10);
        assertThat(result.getSuccessRows()).isEqualTo(10);
        assertThat(result.getErrorRows()).isZero();
    }

    @Test
    @DisplayName("metric_code 空 → 按文件内自增序号 autoSeq 生成 M_{autoSeq:04d}（DB 现有 M_ 最大号+1 递增）")
    void execute_emptyMetricCode_generates() {
        // 空 code 时按 autoSeq 递增（DB 无 M_ 现存 → 从 1 起），与 indexNo 列无关：
        // 第 2 行 indexNo=42 但仍生成 M_0002，防止 indexNo 稀疏/重复导致的自动编码冲突。
        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "存款余额", null, "规模类", "AUTO", "select 1", "ACTIVE"),
                row(42, 2, "存款日均", "", "规模类", "AUTO", "select 2", "ACTIVE"));
        MultipartFile file = writeExcel(rows);

        strategy.execute(batch, file, ImportContext.EMPTY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateMetricDefCmd>> captor = ArgumentCaptor.forClass(List.class);
        verify(metricDefService).batchUpsertByName(captor.capture(), anyString());
        List<CreateMetricDefCmd> cmds = captor.getValue();
        assertThat(cmds.get(0).getMetricCode()).isEqualTo("M_0001");
        assertThat(cmds.get(1).getMetricCode()).isEqualTo("M_0002");
    }

    @Test
    @DisplayName("文件内 metric_code 重复 → 整批失败 + 不调用 service")
    void execute_duplicateMetricCodeInFile_throwsAllOrNone() {
        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "A", "M_DUP", "规模类", "AUTO", "select 1", "ACTIVE"),
                row(2, 1, "B", "M_DUP", "规模类", "AUTO", "select 2", "ACTIVE"));
        MultipartFile file = writeExcel(rows);

        assertThatThrownBy(() -> strategy.execute(batch, file, ImportContext.EMPTY))
                .isInstanceOf(PerfException.class)
                .satisfies(e -> assertThat(((PerfException) e).getErrorCode())
                        .isEqualTo(PerfErrorCode.IMPORT_BATCH_ALL_OR_NONE_FAILED))
                .hasMessageContaining("M_DUP")
                .hasMessageContaining("第3行")  // Excel 物理行号（第 2 行 fixture = Excel 第 3 行）
                .hasMessageContaining("第2行"); // 首次出现的行号

        verify(metricDefService, never()).batchUpsertByName(anyList(), anyString());
    }

    @Test
    @DisplayName("2级指标 MANUAL → calc_logic_type=EXPR / exprText=calcRule")
    void execute_level2_manual_expr() {
        List<MetricDefImportRow> rows = List.of(
                row(1, 2, "派生指标", "M_EXT", "规模类", "MANUAL", "M0001 + M0002", "ACTIVE"));
        MultipartFile file = writeExcel(rows);

        strategy.execute(batch, file, ImportContext.EMPTY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateMetricDefCmd>> captor = ArgumentCaptor.forClass(List.class);
        verify(metricDefService).batchUpsertByName(captor.capture(), anyString());
        CreateMetricDefCmd cmd = captor.getValue().get(0);
        assertThat(cmd.getCalcMode()).isEqualTo("MANUAL");
        assertThat(cmd.getCalcLogicType()).isEqualTo("EXPR");
        assertThat(cmd.getExprText()).isEqualTo("M0001 + M0002");
        assertThat(cmd.getSqlText()).isNull();
    }

    @Test
    @DisplayName("1级指标 AUTO → calc_logic_type=SQL / sqlText=calcRule / calcFreq=DAY")
    void execute_level1_autoSql() {
        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "系统提取", "M_SYS", "效益类", "AUTO", "select sum(x) from t", "ACTIVE"));
        MultipartFile file = writeExcel(rows);

        strategy.execute(batch, file, ImportContext.EMPTY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateMetricDefCmd>> captor = ArgumentCaptor.forClass(List.class);
        verify(metricDefService).batchUpsertByName(captor.capture(), anyString());
        CreateMetricDefCmd cmd = captor.getValue().get(0);
        assertThat(cmd.getCalcMode()).isEqualTo("AUTO");
        assertThat(cmd.getCalcLogicType()).isEqualTo("SQL");
        assertThat(cmd.getSqlText()).isEqualTo("select sum(x) from t");
        assertThat(cmd.getCalcFreq()).isEqualTo("DAY");
        assertThat(cmd.getMetricCategory()).isEqualTo("效益类");
    }

    @Test
    @DisplayName("V1.9：statusFlag=1 → cmd.status=ACTIVE，落库尊重 Excel 意图")
    void execute_statusFlag1_setsActive() {
        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "启用指标", "M_S_ON", "规模类", "AUTO", "select 1", "ACTIVE"));
        MultipartFile file = writeExcel(rows);

        strategy.execute(batch, file, ImportContext.EMPTY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateMetricDefCmd>> captor = ArgumentCaptor.forClass(List.class);
        verify(metricDefService).batchUpsertByName(captor.capture(), anyString());
        assertThat(captor.getValue().get(0).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("V1.9：statusFlag=0 → cmd.status=DISABLED，导入即停用")
    void execute_statusFlag0_setsDisabled() {
        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "停用指标", "M_S_OFF", "规模类", "AUTO", "select 1", "DISABLED"));
        MultipartFile file = writeExcel(rows);

        strategy.execute(batch, file, ImportContext.EMPTY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateMetricDefCmd>> captor = ArgumentCaptor.forClass(List.class);
        verify(metricDefService).batchUpsertByName(captor.capture(), anyString());
        assertThat(captor.getValue().get(0).getStatus()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("导入指标 baseDim 透传（模板「基础维度」列 EMP/ORG/CUST，归一大写）")
    void execute_baseDim_passedThroughFromExcel() {
        // 模板新增「基础维度」列后，baseDim 必填并透传到 cmd（不再默认 null / 维度无关型）
        MetricDefImportRow r = row(1, 1, "机构指标", "M_META", "规模类", "AUTO", "select 1", "ACTIVE");
        r.setBaseDim("org"); // 小写：验证生产侧 trim().toUpperCase() 归一
        List<MetricDefImportRow> rows = List.of(r);
        MultipartFile file = writeExcel(rows);

        strategy.execute(batch, file, ImportContext.EMPTY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateMetricDefCmd>> captor = ArgumentCaptor.forClass(List.class);
        verify(metricDefService).batchUpsertByName(captor.capture(), anyString());
        CreateMetricDefCmd cmd = captor.getValue().get(0);
        assertThat(cmd.getBaseDim()).isEqualTo("ORG");
    }

    @Test
    @DisplayName("空 calcRule 不再由 strategy 拦截 → 透传到 service（空 SQL 合法性由 MetricDefService.create 校验）")
    void execute_emptyCalcRule_delegatesToService() {
        // 早期「来源=2 空 calcRule 整批失败」拦截已移除：strategy 只做翻译/去重/格式级 all-or-none，
        // 空 SQL 由服务层 create() 以 METRIC_CALC_LOGIC_INVALID 校验（见 MetricDefServiceTest）。
        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "缺SQL", "M_NOSQL", "规模类", "AUTO", "", "ACTIVE"));
        MultipartFile file = writeExcel(rows);

        strategy.execute(batch, file, ImportContext.EMPTY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateMetricDefCmd>> captor = ArgumentCaptor.forClass(List.class);
        verify(metricDefService).batchUpsertByName(captor.capture(), anyString());
        // EasyExcel 空单元格回读为 null；strategy 不校验空 SQL，原样透传（null/空）
        assertThat(captor.getValue().get(0).getSqlText()).isNullOrEmpty();
    }

    @Test
    @DisplayName("calcMode 非法 → 整批失败")
    void execute_invalidCalcMode_throws() {
        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "X", "M_X", "规模类", "INVALID", "select 1", "ACTIVE"));
        MultipartFile file = writeExcel(rows);

        assertThatThrownBy(() -> strategy.execute(batch, file, ImportContext.EMPTY))
                .isInstanceOf(PerfException.class)
                .satisfies(e -> assertThat(((PerfException) e).getErrorCode())
                        .isEqualTo(PerfErrorCode.IMPORT_BATCH_ALL_OR_NONE_FAILED));
        verify(metricDefService, never()).batchUpsertByName(anyList(), anyString());
    }

    @Test
    @DisplayName("statusFlag=2 越界 → 整批失败")
    void execute_invalidStatus_throws() {
        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "X", "M_X", "规模类", "AUTO", "select 1", "INVALID"));
        MultipartFile file = writeExcel(rows);

        assertThatThrownBy(() -> strategy.execute(batch, file, ImportContext.EMPTY))
                .isInstanceOf(PerfException.class)
                .satisfies(e -> assertThat(((PerfException) e).getErrorCode())
                        .isEqualTo(PerfErrorCode.IMPORT_BATCH_ALL_OR_NONE_FAILED));
    }

    @Test
    @DisplayName("V1.11：DB 已存在 metric_name → 走 update 路径（不再抛失败），调 batchUpsertByName")
    void execute_dbNameExists_goesUpdatePath() {
        // 默认 mock 已返回 BatchUpsertMetricDefResult(size, 0, ...)，本测试覆盖单条命中 update 路径
        when(metricDefService.batchUpsertByName(anyList(), anyString()))
                .thenReturn(new BatchUpsertMetricDefResult(0, 1, Collections.emptyList()));

        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "已有指标名", "M_NEW_CODE", "规模类", "AUTO", "select 1", "ACTIVE"));
        MultipartFile file = writeExcel(rows);

        ImportResult result = strategy.execute(batch, file, ImportContext.EMPTY);

        // 不抛异常，走 update 路径
        verify(metricDefService, times(1)).batchUpsertByName(anyList(), anyString());
        assertThat(result.getTotalRows()).isEqualTo(1);
        assertThat(result.getSuccessRows()).isEqualTo(1);
        assertThat(result.getUpdatedRows()).isEqualTo(1);
        assertThat(result.getErrorRows()).isZero();
    }

    @Test
    @DisplayName("V1.11：混合 3 新增 + 2 更新 → ImportResult 计数正确")
    void execute_mixed_correctCounts() {
        when(metricDefService.batchUpsertByName(anyList(), anyString()))
                .thenReturn(new BatchUpsertMetricDefResult(3, 2, Collections.emptyList()));

        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "新A", "M_A", "规模类", "AUTO", "select 1", "ACTIVE"),
                row(2, 1, "新B", "M_B", "规模类", "AUTO", "select 1", "ACTIVE"),
                row(3, 1, "更C", "M_C", "规模类", "AUTO", "select 1", "ACTIVE"),
                row(4, 1, "新D", "M_D", "规模类", "AUTO", "select 1", "ACTIVE"),
                row(5, 1, "更E", "M_E", "规模类", "AUTO", "select 1", "ACTIVE"));
        MultipartFile file = writeExcel(rows);

        ImportResult result = strategy.execute(batch, file, ImportContext.EMPTY);

        assertThat(result.getTotalRows()).isEqualTo(5);
        assertThat(result.getSuccessRows()).isEqualTo(5);
        assertThat(result.getUpdatedRows()).isEqualTo(2);
        assertThat(result.getErrorRows()).isZero();
    }

    @Test
    @DisplayName("V1.11：文件内 metric_name 重复 → 整批失败 + 不调用 service")
    void execute_duplicateMetricNameInFile_throws() {
        List<MetricDefImportRow> rows = List.of(
                row(1, 1, "重名指标", "M_NAME_DUP_A", "规模类", "AUTO", "select 1", "ACTIVE"),
                row(2, 1, "重名指标", "M_NAME_DUP_B", "规模类", "AUTO", "select 2", "ACTIVE"));
        MultipartFile file = writeExcel(rows);

        assertThatThrownBy(() -> strategy.execute(batch, file, ImportContext.EMPTY))
                .isInstanceOf(PerfException.class)
                .satisfies(e -> assertThat(((PerfException) e).getErrorCode())
                        .isEqualTo(PerfErrorCode.IMPORT_BATCH_ALL_OR_NONE_FAILED))
                .hasMessageContaining("重名指标")
                .hasMessageContaining("第3行")  // 第 2 行 fixture = Excel 第 3 行
                .hasMessageContaining("第2行"); // 首次出现的行号

        verify(metricDefService, never()).batchUpsertByName(anyList(), anyString());
    }

    // ===== fixture helpers =====

    private static MetricDefImportRow row(Integer indexNo, Integer level, String name, String code,
                                          String category, String calcMode, String rule,
                                          String status) {
        MetricDefImportRow r = new MetricDefImportRow();
        r.setIndexNo(indexNo);
        r.setMetricLevel(level);
        r.setMetricName(name);
        r.setMetricCode(code);
        // 基础维度必填（模板新增「基础维度（EMP/ORG/CUST）」列）：fixture 默认 EMP，
        // 需覆盖具体维度的用例（如透传断言）可单独 setBaseDim 覆盖。
        r.setBaseDim("EMP");
        r.setMetricCategory(category);
        r.setCalcMode(calcMode);
        r.setCalcRule(rule);
        r.setStatus(status);
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
