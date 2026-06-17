package com.bank.branch.platform.performance.service.importer.impl;

import com.bank.branch.platform.auth.api.RoleApi;
import com.bank.branch.platform.auth.api.dto.RoleRespDTO;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfKpiSchemeMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.service.importer.ImportContext;
import com.bank.branch.platform.performance.service.importer.ImportResult;
import com.bank.branch.platform.performance.service.importer.KpiSchemeImportWriter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KpiSchemeImportStrategy 单元测试（2026-06-17，importType=KPI_SCHEME）.
 *
 * <p>覆盖：
 * <ul>
 *   <li>importType()=="KPI_SCHEME"</li>
 *   <li>happy path：1 方案 2 指标行（FORMULA + SQL）→ Writer 收到 2 个 PerfKpiItem，字段正确</li>
 *   <li>指标不存在 / 表达式类型非法 / 角色名不存在 → 抛 PerfException，never 调 Writer</li>
 *   <li>表达式为空 → 通过，formula/sqlExpr 皆空</li>
 *   <li>角色范围多个名 → roleCode CSV 正确</li>
 * </ul>
 *
 * <p>方案新建 vs 复用属 {@link KpiSchemeImportWriter} 职责（落库层），本策略层只负责解析+校验+转换。
 */
class KpiSchemeImportStrategyTest {

    private static final ImportContext CTX = ImportContext.EMPTY;

    private PerfMetricDefMapper metricDefMapper;
    private PerfKpiSchemeMapper schemeMapper;
    private RoleApi roleApi;
    private KpiSchemeImportWriter writer;
    private KpiSchemeImportStrategy strategy;
    private PerfImportBatch batch;

    @BeforeEach
    void setUp() {
        metricDefMapper = mock(PerfMetricDefMapper.class);
        schemeMapper = mock(PerfKpiSchemeMapper.class);
        roleApi = mock(RoleApi.class);
        writer = mock(KpiSchemeImportWriter.class);
        strategy = new KpiSchemeImportStrategy(metricDefMapper, schemeMapper, roleApi, writer);

        // 指标定义模拟：存款余额 → M_0001（EMP），中间业务收入 → M_0002（ORG）
        lenient().when(metricDefMapper.selectByMetricNames(anyList()))
                .thenAnswer(inv -> {
                    List<String> names = inv.getArgument(0);
                    List<PerfMetricDef> defs = new ArrayList<>();
                    for (String n : names) {
                        if ("存款余额".equals(n)) {
                            defs.add(metricDef("M_0001", n, "EMP"));
                        } else if ("中间业务收入".equals(n)) {
                            defs.add(metricDef("M_0002", n, "ORG"));
                        }
                    }
                    return defs;
                });

        // 角色模拟：理财经理 → R_FIN_MGR，柜员 → R_TELLER
        lenient().when(roleApi.listEnabledRoles()).thenReturn(List.of(
                role("R_FIN_MGR", "理财经理"),
                role("R_TELLER", "柜员")));

        // 默认方案为新方案
        lenient().when(schemeMapper.selectBySchemeCode(anyString())).thenReturn(null);

        batch = new PerfImportBatch();
        batch.setId("BATCH_KPI_001");
        batch.setBatchNo("IMP_KPI_001");
        batch.setImportType("KPI_SCHEME");
        batch.setCreatedBy("admin");
    }

    @Test
    @DisplayName("importType 返回 KPI_SCHEME")
    void importType_returnsKpiScheme() {
        assertThat(strategy.importType()).isEqualTo("KPI_SCHEME");
    }

    @Test
    @DisplayName("happy path：1 方案 2 指标行（FORMULA + SQL）→ Writer 收到 2 个 item，字段正确")
    void execute_happyPath_writerReceivesTwoItems() {
        List<Object[]> rows = new ArrayList<>();
        // 序号,方案编号,方案名称,角色范围,指标名,表达式类型,表达式,权重,计分上线,计分下限（已取消「维度」列）
        rows.add(new Object[]{1, "KPI_A", "方案A", "理财经理,柜员", "存款余额",
                "计算表达式", "min(actual/target*weight,100)", new BigDecimal("10"),
                new BigDecimal("120"), new BigDecimal("10")});
        rows.add(new Object[]{2, "KPI_A", "方案A", "理财经理,柜员", "中间业务收入",
                "SQL表达式", "SUM(#{slot1})", new BigDecimal("20"),
                new BigDecimal("130"), new BigDecimal("5")});

        MultipartFile file = writeExcel(rows);
        ImportResult result = strategy.execute(batch, file, CTX);

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getSuccessRows()).isEqualTo(2);
        assertThat(result.getErrorRows()).isZero();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PerfKpiItem>> itemsCap = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> codesCap = ArgumentCaptor.forClass(List.class);
        verify(writer, times(1)).write(itemsCap.capture(), codesCap.capture(),
                anyMap(), anyString());

        List<PerfKpiItem> items = itemsCap.getValue();
        assertThat(items).hasSize(2);

        PerfKpiItem i0 = items.get(0);
        assertThat(i0.getMetricCode()).isEqualTo("M_0001");
        assertThat(i0.getBaseDim()).isEqualTo("EMP"); // 取自指标定义维度
        assertThat(i0.getFormula()).isEqualTo("min(actual/target*weight,100)");
        assertThat(i0.getSqlExpr()).isNull();
        assertThat(i0.getWeight()).isEqualByComparingTo("10");
        assertThat(i0.getMaxScore()).isEqualByComparingTo("120");
        assertThat(i0.getMinScore()).isEqualByComparingTo("10");

        PerfKpiItem i1 = items.get(1);
        assertThat(i1.getMetricCode()).isEqualTo("M_0002");
        assertThat(i1.getBaseDim()).isEqualTo("ORG");
        assertThat(i1.getSqlExpr()).isEqualTo("SUM(#{slot1})");
        assertThat(i1.getFormula()).isNull();

        assertThat(codesCap.getValue()).containsExactly("KPI_A", "KPI_A");
    }

    @Test
    @DisplayName("角色范围多个名 → roleCode CSV 正确传给 writer")
    void execute_multiRoleScope_csvOfCodes() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "KPI_A", "方案A", "理财经理,柜员", "存款余额",
                "计算表达式", "x", new BigDecimal("10"), new BigDecimal("120"), new BigDecimal("10")});

        MultipartFile file = writeExcel(rows);
        strategy.execute(batch, file, CTX);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, KpiSchemeImportStrategy.SchemeInfo>> schemeCap =
                ArgumentCaptor.forClass(java.util.Map.class);
        verify(writer, times(1)).write(anyList(), anyList(), schemeCap.capture(), anyString());

        KpiSchemeImportStrategy.SchemeInfo info = schemeCap.getValue().get("KPI_A");
        assertThat(info).isNotNull();
        assertThat(info.schemeName()).isEqualTo("方案A");
        assertThat(info.empRoleScope()).isEqualTo("R_FIN_MGR,R_TELLER");
    }

    @Test
    @DisplayName("角色范围为空 → empRoleScope=null")
    void execute_blankRoleScope_nullScope() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "KPI_A", "方案A", null, "存款余额",
                "计算表达式", "x", new BigDecimal("10"), new BigDecimal("120"), new BigDecimal("10")});

        MultipartFile file = writeExcel(rows);
        strategy.execute(batch, file, CTX);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, KpiSchemeImportStrategy.SchemeInfo>> schemeCap =
                ArgumentCaptor.forClass(java.util.Map.class);
        verify(writer, times(1)).write(anyList(), anyList(), schemeCap.capture(), anyString());

        assertThat(schemeCap.getValue().get("KPI_A").empRoleScope()).isNull();
    }

    @Test
    @DisplayName("表达式为空 → 通过，formula/sqlExpr 皆空")
    void execute_blankExpr_bothNull() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "KPI_A", "方案A", null, "存款余额",
                "计算表达式", null, new BigDecimal("10"), new BigDecimal("120"), new BigDecimal("10")});

        MultipartFile file = writeExcel(rows);
        ImportResult result = strategy.execute(batch, file, CTX);
        assertThat(result.getSuccessRows()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PerfKpiItem>> itemsCap = ArgumentCaptor.forClass(List.class);
        verify(writer, times(1)).write(itemsCap.capture(), anyList(), anyMap(), anyString());

        PerfKpiItem item = itemsCap.getValue().get(0);
        assertThat(item.getFormula()).isNull();
        assertThat(item.getSqlExpr()).isNull();
    }

    @Test
    @DisplayName("指标不存在 → 抛 PerfException 含'指标不存在'，never 调 Writer")
    void execute_unknownMetric_throws() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "KPI_A", "方案A", null, "不存在的指标",
                "计算表达式", "x", new BigDecimal("10"), new BigDecimal("120"), new BigDecimal("10")});

        MultipartFile file = writeExcel(rows);
        assertThatThrownBy(() -> strategy.execute(batch, file, CTX))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("指标不存在");
        verify(writer, never()).write(anyList(), anyList(), anyMap(), anyString());
    }

    @Test
    @DisplayName("表达式类型非法 → 抛错，never 调 Writer")
    void execute_invalidExprType_throws() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "KPI_A", "方案A", null, "存款余额",
                "胡乱类型", "x", new BigDecimal("10"), new BigDecimal("120"), new BigDecimal("10")});

        MultipartFile file = writeExcel(rows);
        assertThatThrownBy(() -> strategy.execute(batch, file, CTX))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("表达式类型非法");
        verify(writer, never()).write(anyList(), anyList(), anyMap(), anyString());
    }

    @Test
    @DisplayName("角色名不存在 → 抛错含'角色不存在'，never 调 Writer")
    void execute_unknownRole_throws() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "KPI_A", "方案A", "理财经理,不存在的角色", "存款余额",
                "计算表达式", "x", new BigDecimal("10"), new BigDecimal("120"), new BigDecimal("10")});

        MultipartFile file = writeExcel(rows);
        assertThatThrownBy(() -> strategy.execute(batch, file, CTX))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("角色不存在");
        verify(writer, never()).write(anyList(), anyList(), anyMap(), anyString());
    }

    @Test
    @DisplayName("方案编号缺失 → 抛错，never 调 Writer")
    void execute_blankSchemeCode_throws() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, null, "方案A", null, "存款余额",
                "计算表达式", "x", new BigDecimal("10"), new BigDecimal("120"), new BigDecimal("10")});

        MultipartFile file = writeExcel(rows);
        assertThatThrownBy(() -> strategy.execute(batch, file, CTX))
                .isInstanceOf(PerfException.class);
        verify(writer, never()).write(anyList(), anyList(), anyMap(), anyString());
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

    private static RoleRespDTO role(String code, String chName) {
        RoleRespDTO r = new RoleRespDTO();
        r.setRoleCode(code);
        r.setRoleChName(chName);
        return r;
    }

    /** 写单 sheet 文件：表头 10 列（已取消「维度」列）+ 给定数据行. */
    private static MultipartFile writeExcel(List<Object[]> rows) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("KPI方案");
            Row header = sheet.createRow(0);
            String[] heads = {"序号", "方案编号", "方案名称", "员工角色范围",
                    "指标名称", "表达式类型", "表达式", "权重", "计分上线", "计分下限"};
            for (int i = 0; i < heads.length; i++) {
                header.createCell(i).setCellValue(heads[i]);
            }
            for (int i = 0; i < rows.size(); i++) {
                Row r = sheet.createRow(i + 1);
                Object[] cells = rows.get(i);
                for (int c = 0; c < cells.length; c++) {
                    Object val = cells[c];
                    if (val == null) {
                        continue;
                    }
                    if (val instanceof Integer) {
                        r.createCell(c).setCellValue(((Integer) val).doubleValue());
                    } else if (val instanceof BigDecimal) {
                        r.createCell(c).setCellValue(((BigDecimal) val).doubleValue());
                    } else {
                        r.createCell(c).setCellValue(String.valueOf(val));
                    }
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return new MockMultipartFile("file", "kpi-scheme.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    bos.toByteArray());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
