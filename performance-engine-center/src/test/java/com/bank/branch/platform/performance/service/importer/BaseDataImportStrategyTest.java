package com.bank.branch.platform.performance.service.importer;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.service.importer.impl.BaseDataImportStrategy;
import com.bank.branch.platform.performance.service.importer.model.BaseDataImportRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BaseDataImportStrategy 单元测试（Task P5.3 Red）.
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>importType 返回 BASE_DATA</li>
 *   <li>3 EMP + 2 ORG + 2 CUST 合法行 → 路由到对应 Mapper 各调 n 次</li>
 *   <li>未知 metricCode / 非法 dataDate / 必填空 → errorSummary 记录，不抛</li>
 *   <li>列头完全不匹配 → 抛 IMPORT_COLUMN_MAPPING_INVALID</li>
 * </ul>
 */
class BaseDataImportStrategyTest {

    private PerfMetricDefMapper metricDefMapper;
    private EmpIndexResultMapper empMapper;
    private OrgIndexResultMapper orgMapper;
    private CustIndexResultMapper custMapper;
    private BaseDataImportStrategy strategy;
    private PerfImportBatch batch;

    @BeforeEach
    void setUp() {
        metricDefMapper = mock(PerfMetricDefMapper.class);
        empMapper = mock(EmpIndexResultMapper.class);
        orgMapper = mock(OrgIndexResultMapper.class);
        custMapper = mock(CustIndexResultMapper.class);
        strategy = new BaseDataImportStrategy(metricDefMapper, empMapper, orgMapper, custMapper);

        when(metricDefMapper.selectByMetricCodes(anyList()))
                .thenAnswer(inv -> {
                    List<String> codes = inv.getArgument(0);
                    List<PerfMetricDef> defs = new ArrayList<>();
                    for (String c : codes) {
                        if ("BASE_EMP_1".equals(c)) {
                            defs.add(metricDef(c, "EMP", 10));
                        } else if ("BASE_ORG_1".equals(c)) {
                            defs.add(metricDef(c, "ORG", 20));
                        } else if ("BASE_CUST_1".equals(c)) {
                            defs.add(metricDef(c, "CUST", 30));
                        }
                    }
                    return defs;
                });

        batch = new PerfImportBatch();
        batch.setId("BATCH_BASE");
        batch.setBatchNo("IMP_BASE_001");
        batch.setImportType("BASE_DATA");
        batch.setCreatedBy("admin");
    }

    @Test
    @DisplayName("importType 返回 BASE_DATA")
    void importType_returnsBaseData() {
        assertThat(strategy.importType()).isEqualTo("BASE_DATA");
    }

    @Test
    @DisplayName("execute：3 EMP + 2 ORG + 2 CUST → 各 Mapper 按次数被调，成功 7 行")
    void execute_routesByBaseDim_toEachMapper() {
        List<BaseDataImportRow> rows = new ArrayList<>();
        // 3 EMP
        rows.add(row("E001", "BASE_EMP_1", "2026-04-01", "20260401", new BigDecimal("10")));
        rows.add(row("E002", "BASE_EMP_1", "2026-04-01", "20260401", new BigDecimal("20")));
        rows.add(row("E003", "BASE_EMP_1", "2026-04-01", "20260401", new BigDecimal("30")));
        // 2 ORG
        rows.add(row("O001", "BASE_ORG_1", "2026-04-01", "20260401", new BigDecimal("100")));
        rows.add(row("O002", "BASE_ORG_1", "2026-04-01", "20260401", new BigDecimal("200")));
        // 2 CUST
        rows.add(row("C001", "BASE_CUST_1", "2026-04-01", "20260401", new BigDecimal("1")));
        rows.add(row("C002", "BASE_CUST_1", "2026-04-01", "20260401", new BigDecimal("2")));

        MultipartFile file = writeExcel(rows);
        ImportResult result = strategy.execute(batch, file);

        assertThat(result.getTotalRows()).isEqualTo(7);
        assertThat(result.getSuccessRows()).isEqualTo(7);
        assertThat(result.getErrorRows()).isEqualTo(0);
        assertThat(result.getErrorSummary()).isNullOrEmpty();

        verify(empMapper, times(3)).insertSlotValue(anyString(), any(LocalDate.class),
                anyString(), eq(10), any(BigDecimal.class));
        verify(orgMapper, times(2)).insertSlotValue(anyString(), any(LocalDate.class),
                anyString(), eq(20), any(BigDecimal.class));
        verify(custMapper, times(2)).insertSlotValue(anyString(), any(LocalDate.class),
                anyString(), eq(30), any(BigDecimal.class));
    }

    @Test
    @DisplayName("execute：未知 metricCode → errorSummary 记录，不抛异常，对应 Mapper 不被调")
    void execute_unknownMetric_recordedInErrorSummary() {
        List<BaseDataImportRow> rows = new ArrayList<>();
        rows.add(row("E001", "UNKNOWN_METRIC", "2026-04-01", "20260401", new BigDecimal("10")));

        MultipartFile file = writeExcel(rows);
        ImportResult result = strategy.execute(batch, file);

        assertThat(result.getTotalRows()).isEqualTo(1);
        assertThat(result.getSuccessRows()).isEqualTo(0);
        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getErrorSummary()).contains("UNKNOWN_METRIC");
        verify(empMapper, never()).insertSlotValue(anyString(), any(), anyString(), ArgumentMatchers.anyInt(), any());
    }

    @Test
    @DisplayName("execute：非法 dataDate → errorSummary 记录，不抛异常")
    void execute_invalidDataDate_recordedInErrorSummary() {
        List<BaseDataImportRow> rows = new ArrayList<>();
        rows.add(row("E001", "BASE_EMP_1", "2026-04-xx", "20260401", new BigDecimal("10")));

        MultipartFile file = writeExcel(rows);
        ImportResult result = strategy.execute(batch, file);

        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getErrorSummary()).contains("第");
        verify(empMapper, never()).insertSlotValue(anyString(), any(), anyString(), ArgumentMatchers.anyInt(), any());
    }

    @Test
    @DisplayName("execute：必填空 → errorSummary 记录（subjectKey / value 分别校验）")
    void execute_requiredMissing_recordedInErrorSummary() {
        List<BaseDataImportRow> rows = new ArrayList<>();
        rows.add(row("", "BASE_EMP_1", "2026-04-01", "20260401", new BigDecimal("10")));  // subjectKey 空
        rows.add(row("E001", "BASE_EMP_1", "2026-04-01", "20260401", null));              // value 空

        MultipartFile file = writeExcel(rows);
        ImportResult result = strategy.execute(batch, file);

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getSuccessRows()).isEqualTo(0);
        assertThat(result.getErrorRows()).isEqualTo(2);
        assertThat(result.getErrorSummary()).isNotBlank();
    }

    @Test
    @DisplayName("execute：列头完全不匹配 → 抛 IMPORT_COLUMN_MAPPING_INVALID")
    void execute_columnMismatch_throwsImportColumnMappingInvalid() {
        MultipartFile file = writeExcelWithWrongHead();

        assertThatThrownBy(() -> strategy.execute(batch, file))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.IMPORT_COLUMN_MAPPING_INVALID));
    }

    // ================ helpers ================

    private static BaseDataImportRow row(String subjectKey, String metricCode, String dataDate,
                                         String version, BigDecimal value) {
        BaseDataImportRow r = new BaseDataImportRow();
        r.setSubjectKey(subjectKey);
        r.setMetricCode(metricCode);
        r.setDataDate(dataDate);
        r.setVersion(version);
        r.setValue(value);
        return r;
    }

    private static PerfMetricDef metricDef(String code, String baseDim, int slot) {
        PerfMetricDef d = new PerfMetricDef();
        d.setMetricCode(code);
        d.setBaseDim(baseDim);
        d.setValSlot(slot);
        d.setStatus("ACTIVE");
        return d;
    }

    private static MultipartFile writeExcel(List<BaseDataImportRow> rows) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        EasyExcel.write(bos, BaseDataImportRow.class).sheet("基础数据").doWrite(rows);
        return new MockMultipartFile("file", "base.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());
    }

    private static MultipartFile writeExcelWithWrongHead() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        List<List<String>> head = new ArrayList<>();
        head.add(List.of("A"));
        head.add(List.of("B"));
        head.add(List.of("C"));
        List<List<Object>> data = new ArrayList<>();
        data.add(List.of("x", "y", "z"));
        EasyExcel.write(bos).head(head).sheet("基础数据").doWrite(data);
        return new MockMultipartFile("file", "wrong.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());
    }

    // 避免 IDE 未使用警告
    @SuppressWarnings("unused")
    private void avoidUnused() {
        atLeastOnce();
    }
}
