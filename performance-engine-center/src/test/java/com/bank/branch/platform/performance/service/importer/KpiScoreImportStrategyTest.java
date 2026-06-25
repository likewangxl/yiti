package com.bank.branch.platform.performance.service.importer;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.entity.PerfKpiScore;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfKpiScoreMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.service.importer.impl.KpiScoreImportStrategy;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link KpiScoreImportStrategy} 单元测试：KPI 结果导入 PERF_KPI_SCORE（整批 all-or-none + upsert）.
 */
class KpiScoreImportStrategyTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 12);
    private static final String SCHEME = "KPI001";
    private static final ImportContext CTX = new ImportContext(DATE, SCHEME);

    private PerfKpiScoreMapper kpiScoreMapper;
    private PerfMetricDefMapper metricDefMapper;
    private OrgApi orgApi;
    private UserApi userApi;
    private KpiScoreImportStrategy strategy;
    private PerfImportBatch batch;

    @BeforeEach
    void setUp() {
        kpiScoreMapper = mock(PerfKpiScoreMapper.class);
        metricDefMapper = mock(PerfMetricDefMapper.class);
        orgApi = mock(OrgApi.class);
        userApi = mock(UserApi.class);
        strategy = new KpiScoreImportStrategy(kpiScoreMapper, metricDefMapper, orgApi, userApi);

        // 指标库：按 (维度+指标名) 命中 metric_code
        lenient().when(metricDefMapper.selectByMetricNames(anyList())).thenReturn(List.of(
                metricDef("M_EMP", "一般性存款月均", "EMP"),
                metricDef("M_ORG", "机构存款余额", "ORG"),
                metricDef("M_CUST", "客户余额", "CUST")));
        // 存在性默认放行
        lenient().when(orgApi.getOrgByDeptNo(anyString())).thenReturn(new OrgDTO());
        lenient().when(userApi.filterExistingUsernames(anyList()))
                .thenAnswer(inv -> new java.util.ArrayList<>(inv.getArgument(0)));
        lenient().when(kpiScoreMapper.upsert(any())).thenReturn(1);

        batch = new PerfImportBatch();
        batch.setId("BATCH_KPI_1");
        batch.setCreatedBy("admin");
    }

    @Test
    @DisplayName("importType 返回 KPI_SCORE")
    void importType_returnsKpiScore() {
        assertThat(strategy.importType()).isEqualTo("KPI_SCORE");
    }

    @Test
    @DisplayName("正常：EMP/ORG/CUST 三行 → upsert 各 1 次，data_date/scheme_code 取自页面，subject/指标码正确")
    void execute_happyPath_upsertsAllRows() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"EMP", "一般性存款月均", "11051776", 90, 100, 50, 110, 80});
        rows.add(new Object[]{"ORG", "机构存款余额", "107", 88, 200, 30, 220, 180});
        rows.add(new Object[]{"CUST", "客户余额", "C001", 77, 300, 20, 330, 280});

        ImportResult r = strategy.execute(batch, writeExcel(rows), CTX);

        assertThat(r.getTotalRows()).isEqualTo(3);
        assertThat(r.getSuccessRows()).isEqualTo(3);
        assertThat(r.getErrorRows()).isZero();

        org.mockito.ArgumentCaptor<PerfKpiScore> cap = org.mockito.ArgumentCaptor.forClass(PerfKpiScore.class);
        verify(kpiScoreMapper, times(3)).upsert(cap.capture());
        List<PerfKpiScore> ups = cap.getAllValues();
        assertThat(ups).allSatisfy(s -> {
            assertThat(s.getDataDate()).isEqualTo(DATE);
            assertThat(s.getSchemeCode()).isEqualTo(SCHEME);
        });
        PerfKpiScore emp = ups.get(0);
        assertThat(emp.getSubjectType()).isEqualTo("EMP");
        assertThat(emp.getSubjectId()).isEqualTo("11051776");
        assertThat(emp.getMetricCode()).isEqualTo("M_EMP");
        assertThat(emp.getScore()).isEqualByComparingTo("90");
        assertThat(emp.getActualValue()).isEqualByComparingTo("100");
        assertThat(emp.getWeight()).isEqualByComparingTo("50");
        assertThat(emp.getTargetValue()).isEqualByComparingTo("110");
        assertThat(emp.getBaseValue()).isEqualByComparingTo("80");
        assertThat(ups.get(1).getMetricCode()).isEqualTo("M_ORG");
        assertThat(ups.get(2).getMetricCode()).isEqualTo("M_CUST");
    }

    @Test
    @DisplayName("指标名称匹配不上 → 整批失败抛异常，不 upsert 任何行")
    void execute_metricNameNotMatched_throwsAndNoUpsert() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"EMP", "一般性存款月均", "11051776", 90, 100, 50, 110, 80});
        rows.add(new Object[]{"EMP", "不存在的指标", "11051776", 50, 60, 10, 70, 40});

        assertThatThrownBy(() -> strategy.execute(batch, writeExcel(rows), CTX))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("指标名称匹配不上")
                .hasMessageContaining("不存在的指标");
        verify(kpiScoreMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("ORG 维度对象不在机构表 → 整批失败，不 upsert")
    void execute_orgNotExist_throws() {
        when(orgApi.getOrgByDeptNo("999")).thenReturn(null);
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"ORG", "机构存款余额", "999", 88, 200, 30, 220, 180});

        assertThatThrownBy(() -> strategy.execute(batch, writeExcel(rows), CTX))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("机构不存在").hasMessageContaining("999");
        verify(kpiScoreMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("EMP 维度对象不在 PT_USER 工号 → 整批失败，不 upsert")
    void execute_empNotExist_throws() {
        when(userApi.filterExistingUsernames(List.of("GHOST"))).thenReturn(List.of());
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"EMP", "一般性存款月均", "GHOST", 90, 100, 50, 110, 80});

        assertThatThrownBy(() -> strategy.execute(batch, writeExcel(rows), CTX))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("员工不存在").hasMessageContaining("GHOST");
        verify(kpiScoreMapper, never()).upsert(any());
    }

    @Test
    @DisplayName("缺数据日期 / 缺方案编码 → fail-fast 抛异常")
    void execute_missingCtx_throws() {
        MultipartFile f = writeExcel(List.<Object[]>of(new Object[]{"EMP", "一般性存款月均", "11051776", 90, 100, 50, 110, 80}));
        assertThatThrownBy(() -> strategy.execute(batch, f, new ImportContext(null, SCHEME)))
                .isInstanceOf(PerfException.class).hasMessageContaining("数据日期必填");
        assertThatThrownBy(() -> strategy.execute(batch, f, new ImportContext(DATE, "  ")))
                .isInstanceOf(PerfException.class).hasMessageContaining("方案编码必填");
    }

    @Test
    @DisplayName("重复行命中唯一键 → upsert 返回 2，updatedRows 计数")
    void execute_duplicate_countsUpdated() {
        when(kpiScoreMapper.upsert(any())).thenReturn(2); // MySQL 更新返回 2
        ImportResult r = strategy.execute(batch,
                writeExcel(List.<Object[]>of(new Object[]{"EMP", "一般性存款月均", "11051776", 90, 100, 50, 110, 80})), CTX);
        assertThat(r.getUpdatedRows()).isEqualTo(1);
    }

    // ================ helpers ================

    private static PerfMetricDef metricDef(String code, String name, String baseDim) {
        PerfMetricDef d = new PerfMetricDef();
        d.setMetricCode(code);
        d.setMetricName(name);
        d.setBaseDim(baseDim);
        d.setStatus("ACTIVE");
        return d;
    }

    /** 写 8 列模板：维度/指标名称/维度对象/得分/实际值/权重(%)/目标值/基础值. */
    private static MultipartFile writeExcel(List<Object[]> rows) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("KPI结果");
            String[] headers = {"维度", "指标名称", "维度对象", "得分", "实际值", "权重(%)", "目标值", "基础值"};
            Row h = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                h.createCell(i).setCellValue(headers[i]);
            }
            for (int i = 0; i < rows.size(); i++) {
                Row r = sheet.createRow(i + 1);
                Object[] cells = rows.get(i);
                for (int c = 0; c < cells.length; c++) {
                    if (cells[c] == null) {
                        continue;
                    }
                    if (cells[c] instanceof Number n) {
                        r.createCell(c).setCellValue(n.doubleValue());
                    } else {
                        r.createCell(c).setCellValue(String.valueOf(cells[c]));
                    }
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return new MockMultipartFile("file", "kpi.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
