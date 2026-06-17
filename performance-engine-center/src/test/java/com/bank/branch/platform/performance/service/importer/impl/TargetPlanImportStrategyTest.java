package com.bank.branch.platform.performance.service.importer.impl;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetPlanMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetValueMapper;
import com.bank.branch.platform.performance.service.importer.ImportContext;
import com.bank.branch.platform.performance.service.importer.ImportResult;
import com.bank.branch.platform.performance.service.importer.TargetPlanImportWriter;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TargetPlanImportStrategy 单元测试（2026-06-17，importType=TARGET_PLAN）.
 *
 * <p>覆盖：
 * <ul>
 *   <li>happy path：2 行同方案不同指标/阶段 → Writer 收到 2 条 value + 字段正确</li>
 *   <li>维度非法 / 指标名不存在 / 目标值缺失 → 抛 IMPORT_BATCH_ALL_OR_NONE_FAILED，never 调 Writer</li>
 *   <li>ORG 维度 → getOrgByDeptNo 归一为机构编码后入 subjectId</li>
 * </ul>
 *
 * <p>方案是否重建（复用既有 id vs 新建）属 {@link TargetPlanImportWriter} 职责，单测见
 * {@code TargetPlanImportWriterTest}（落库层）。本策略层只负责解析+校验+转换。
 */
class TargetPlanImportStrategyTest {

    private static final ImportContext CTX = ImportContext.EMPTY;

    private PerfMetricDefMapper metricDefMapper;
    private OrgApi orgApi;
    private UserApi userApi;
    private PerfTargetPlanMapper targetPlanMapper;
    private PerfTargetValueMapper targetValueMapper;
    private TargetPlanImportWriter writer;
    private TargetPlanImportStrategy strategy;
    private PerfImportBatch batch;

    @BeforeEach
    void setUp() {
        metricDefMapper = mock(PerfMetricDefMapper.class);
        orgApi = mock(OrgApi.class);
        userApi = mock(UserApi.class);
        targetPlanMapper = mock(PerfTargetPlanMapper.class);
        targetValueMapper = mock(PerfTargetValueMapper.class);
        writer = mock(TargetPlanImportWriter.class);
        strategy = new TargetPlanImportStrategy(metricDefMapper, orgApi, userApi,
                targetPlanMapper, targetValueMapper, writer);

        // 默认所有请求的工号都存在（回显工号本身）；个别用例可覆盖为不存在
        lenient().when(userApi.filterExistingUsernames(anyList()))
                .thenAnswer(inv -> new ArrayList<>(inv.getArgument(0)));

        // 默认方案为新方案（DB 无既有目标值行 → 重叠校验只看文件内）
        lenient().when(targetPlanMapper.selectByPlanCode(anyString())).thenReturn(null);

        // 指标定义模拟：存款余额 → M_0001（EMP），中收 → M_0002（ORG）
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

        // 机构默认归一：部门编号 D001 → 机构编码 ORG_001
        lenient().when(orgApi.getOrgByDeptNo(anyString())).thenAnswer(inv -> {
            OrgDTO o = new OrgDTO();
            o.setOrgCode("ORG_" + inv.getArgument(0));
            return o;
        });

        batch = new PerfImportBatch();
        batch.setId("BATCH_TP_001");
        batch.setBatchNo("IMP_TP_001");
        batch.setImportType("TARGET_PLAN");
        batch.setCreatedBy("admin");
    }

    @Test
    @DisplayName("importType 返回 TARGET_PLAN")
    void importType_returnsTargetPlan() {
        assertThat(strategy.importType()).isEqualTo("TARGET_PLAN");
    }

    @Test
    @DisplayName("happy path：2 行同方案不同指标/阶段 → Writer 收到 2 条 value，字段正确")
    void execute_happyPath_writerReceivesTwoValues() {
        List<Object[]> rows = new ArrayList<>();
        // 序号,编号,名称,阶段名,起始,截止,维度,工号/部门,指标名,目标值,基础值
        rows.add(new Object[]{1, "PLAN_A", "方案A", "Q1", 20260101, 20260331,
                "员工", "emp001", "存款余额", new BigDecimal("100.50"), new BigDecimal("80")});
        rows.add(new Object[]{2, "PLAN_A", "方案A", "Q2", 20260401, 20260630,
                "员工", "emp001", "存款余额", new BigDecimal("120"), null});

        MultipartFile file = writeExcel(rows);
        ImportResult result = strategy.execute(batch, file, CTX);

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getSuccessRows()).isEqualTo(2);
        assertThat(result.getErrorRows()).isZero();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PerfTargetValue>> valuesCap = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> codesCap = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> namesCap = ArgumentCaptor.forClass(List.class);
        verify(writer, times(1)).write(valuesCap.capture(), codesCap.capture(),
                namesCap.capture(), anyString());

        List<PerfTargetValue> values = valuesCap.getValue();
        assertThat(values).hasSize(2);

        PerfTargetValue v0 = values.get(0);
        assertThat(v0.getSubjectType()).isEqualTo("EMP");
        assertThat(v0.getSubjectId()).isEqualTo("emp001");
        assertThat(v0.getMetricCode()).isEqualTo("M_0001");
        assertThat(v0.getStageName()).isEqualTo("Q1");
        assertThat(v0.getStartDate().toString()).isEqualTo("2026-01-01");
        assertThat(v0.getEndDate().toString()).isEqualTo("2026-03-31");
        assertThat(v0.getTargetValue()).isEqualByComparingTo("100.50");
        assertThat(v0.getBaseValue()).isEqualByComparingTo("80");
        // cycle_key 派生自起始年份
        assertThat(v0.getCycleKey()).isEqualTo("2026");

        PerfTargetValue v1 = values.get(1);
        assertThat(v1.getStageName()).isEqualTo("Q2");
        assertThat(v1.getBaseValue()).isNull();

        assertThat(codesCap.getValue()).containsExactly("PLAN_A", "PLAN_A");
        assertThat(namesCap.getValue()).containsExactly("方案A", "方案A");
    }

    @Test
    @DisplayName("ORG 维度：getOrgByDeptNo 归一为机构编码后入 subjectId")
    void execute_orgDim_normalizedToOrgCode() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "PLAN_B", "方案B", "Y2026", 20260101, 20261231,
                "机构", "D001", "中间业务收入", new BigDecimal("500"), null});

        MultipartFile file = writeExcel(rows);
        ImportResult result = strategy.execute(batch, file, CTX);

        assertThat(result.getSuccessRows()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PerfTargetValue>> valuesCap = ArgumentCaptor.forClass(List.class);
        verify(writer, times(1)).write(valuesCap.capture(), anyList(), anyList(), anyString());

        PerfTargetValue v = valuesCap.getValue().get(0);
        assertThat(v.getSubjectType()).isEqualTo("ORG");
        // 部门编号 D001 → 机构编码 ORG_D001（由 mock 归一）
        assertThat(v.getSubjectId()).isEqualTo("ORG_D001");
        assertThat(v.getMetricCode()).isEqualTo("M_0002");
        verify(orgApi, times(1)).getOrgByDeptNo("D001");
    }

    @Test
    @DisplayName("维度非法 → 抛 IMPORT_BATCH_ALL_OR_NONE_FAILED，never 调 Writer")
    void execute_invalidDim_throwsAllOrNone() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "PLAN_A", "方案A", "Q1", 20260101, 20260331,
                "客户", "x001", "存款余额", new BigDecimal("1"), null});

        MultipartFile file = writeExcel(rows);
        assertThatThrownBy(() -> strategy.execute(batch, file, CTX))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("维度非法");
        verify(writer, never()).write(anyList(), anyList(), anyList(), anyString());
    }

    @Test
    @DisplayName("指标名不存在 → 抛 IMPORT_BATCH_ALL_OR_NONE_FAILED，never 调 Writer")
    void execute_unknownMetric_throwsAllOrNone() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "PLAN_A", "方案A", "Q1", 20260101, 20260331,
                "员工", "emp001", "不存在的指标", new BigDecimal("1"), null});

        MultipartFile file = writeExcel(rows);
        assertThatThrownBy(() -> strategy.execute(batch, file, CTX))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("指标不存在");
        verify(writer, never()).write(anyList(), anyList(), anyList(), anyString());
    }

    @Test
    @DisplayName("目标值缺失 → 抛 IMPORT_BATCH_ALL_OR_NONE_FAILED，never 调 Writer")
    void execute_missingTargetValue_throwsAllOrNone() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "PLAN_A", "方案A", "Q1", 20260101, 20260331,
                "员工", "emp001", "存款余额", null, null});

        MultipartFile file = writeExcel(rows);
        assertThatThrownBy(() -> strategy.execute(batch, file, CTX))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("目标值必填");
        verify(writer, never()).write(anyList(), anyList(), anyList(), anyString());
    }

    @Test
    @DisplayName("any 行失败 → 整批不调 Writer（all-or-none：好行也不写）")
    void execute_oneBadRowAmongGood_neverWrites() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "PLAN_A", "方案A", "Q1", 20260101, 20260331,
                "员工", "emp001", "存款余额", new BigDecimal("1"), null});
        rows.add(new Object[]{2, "PLAN_A", "方案A", "Q2", 20260401, 20260630,
                "无效维度", "emp001", "存款余额", new BigDecimal("2"), null});

        MultipartFile file = writeExcel(rows);
        assertThatThrownBy(() -> strategy.execute(batch, file, CTX))
                .isInstanceOf(PerfException.class);
        verify(writer, never()).write(anyList(), anyList(), anyList(), anyString());
    }

    @Test
    @DisplayName("维度与指标不匹配（员工维度配 ORG 指标）→ 抛错，never 调 Writer")
    void execute_dimMetricMismatch_throwsAllOrNone() {
        List<Object[]> rows = new ArrayList<>();
        // 维度=员工，但"中间业务收入"是 ORG 指标
        rows.add(new Object[]{1, "PLAN_A", "方案A", "Q1", 20260101, 20260331,
                "员工", "emp001", "中间业务收入", new BigDecimal("1"), null});

        MultipartFile file = writeExcel(rows);
        assertThatThrownBy(() -> strategy.execute(batch, file, CTX))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("维度与指标不匹配");
        verify(writer, never()).write(anyList(), anyList(), anyList(), anyString());
    }

    @Test
    @DisplayName("EMP 对象不存在（UserApi 查无此工号）→ 抛错，never 调 Writer")
    void execute_empNotExist_throwsAllOrNone() {
        // 覆盖默认：任何工号都查不到
        when(userApi.filterExistingUsernames(anyList())).thenReturn(List.of());

        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "PLAN_A", "方案A", "Q1", 20260101, 20260331,
                "员工", "ghost999", "存款余额", new BigDecimal("1"), null});

        MultipartFile file = writeExcel(rows);
        assertThatThrownBy(() -> strategy.execute(batch, file, CTX))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("员工不存在");
        verify(writer, never()).write(anyList(), anyList(), anyList(), anyString());
    }

    @Test
    @DisplayName("ORG 对象不存在（部门编号查无机构）→ 抛错，never 调 Writer")
    void execute_orgNotExist_throwsAllOrNone() {
        when(orgApi.getOrgByDeptNo(anyString())).thenReturn(null);

        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "PLAN_B", "方案B", "Y2026", 20260101, 20261231,
                "机构", "D404", "中间业务收入", new BigDecimal("1"), null});

        MultipartFile file = writeExcel(rows);
        assertThatThrownBy(() -> strategy.execute(batch, file, CTX))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("机构不存在");
        verify(writer, never()).write(anyList(), anyList(), anyList(), anyString());
    }

    @Test
    @DisplayName("同方案同对象同指标的不同阶段日期重叠 → 抛错，never 调 Writer")
    void execute_dateOverlap_throwsAllOrNone() {
        List<Object[]> rows = new ArrayList<>();
        // Q1: 0101~0331，Q2: 0301~0630 → 0301~0331 重叠
        rows.add(new Object[]{1, "PLAN_A", "方案A", "Q1", 20260101, 20260331,
                "员工", "emp001", "存款余额", new BigDecimal("1"), null});
        rows.add(new Object[]{2, "PLAN_A", "方案A", "Q2", 20260301, 20260630,
                "员工", "emp001", "存款余额", new BigDecimal("2"), null});

        MultipartFile file = writeExcel(rows);
        assertThatThrownBy(() -> strategy.execute(batch, file, CTX))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("重叠");
        verify(writer, never()).write(anyList(), anyList(), anyList(), anyString());
    }

    @Test
    @DisplayName("不同对象同指标日期'重叠'不算冲突（分组隔离）→ 正常写入")
    void execute_overlapAcrossDifferentSubjects_writes() {
        List<Object[]> rows = new ArrayList<>();
        // 同方案同指标同阶段，但不同员工，区间相同 → 不应判为重叠
        rows.add(new Object[]{1, "PLAN_A", "方案A", "Q1", 20260101, 20260331,
                "员工", "emp001", "存款余额", new BigDecimal("1"), null});
        rows.add(new Object[]{2, "PLAN_A", "方案A", "Q1", 20260101, 20260331,
                "员工", "emp002", "存款余额", new BigDecimal("2"), null});

        MultipartFile file = writeExcel(rows);
        ImportResult result = strategy.execute(batch, file, CTX);
        assertThat(result.getSuccessRows()).isEqualTo(2);
        verify(writer, times(1)).write(anyList(), anyList(), anyList(), anyString());
    }

    @Test
    @DisplayName("重导入已存在方案：用 selectAllByPlanId 批量拉取，绝不逐组 selectByPlanSubjectMetric；与既有 DB 行重叠则报错")
    void execute_existingPlanReimport_usesBulkLoad_noPerGroupQuery() {
        // 方案已存在 → planId=P1
        PerfTargetPlan plan = new PerfTargetPlan();
        plan.setId("P1");
        plan.setPlanCode("PLAN_A");
        when(targetPlanMapper.selectByPlanCode("PLAN_A")).thenReturn(plan);

        // DB 既有一条 emp001 / M_0001 / 阶段 OLD：2/01~2/28，与文件 Q1(1/01~3/31) 重叠
        PerfTargetValue dbRow = new PerfTargetValue();
        dbRow.setSubjectType("EMP");
        dbRow.setSubjectId("emp001");
        dbRow.setMetricCode("M_0001");
        dbRow.setStageName("OLD");
        dbRow.setStartDate(LocalDate.of(2026, 2, 1));
        dbRow.setEndDate(LocalDate.of(2026, 2, 28));
        when(targetValueMapper.selectAllByPlanId("P1")).thenReturn(List.of(dbRow));

        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "PLAN_A", "方案A", "Q1", 20260101, 20260331,
                "员工", "emp001", "存款余额", new BigDecimal("1"), null});

        MultipartFile file = writeExcel(rows);
        assertThatThrownBy(() -> strategy.execute(batch, file, CTX))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("重叠");

        // 关键：单次批量拉取，绝不逐 (对象,指标) 组查询（避免 5 万行 N+1）
        verify(targetValueMapper, times(1)).selectAllByPlanId("P1");
        verify(targetValueMapper, never()).selectByPlanSubjectMetric(
                anyString(), anyString(), anyString(), anyString());
        verify(writer, never()).write(anyList(), anyList(), anyList(), anyString());
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

    /** 写单 sheet 文件：表头 11 列 + 给定数据行. */
    private static MultipartFile writeExcel(List<Object[]> rows) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("目标方案");
            Row header = sheet.createRow(0);
            String[] heads = {"序号", "目标方案编号", "目标方案名称", "阶段名称",
                    "阶段起始日期", "阶段截止日期", "维度", "工号/部门编号",
                    "指标名称", "目标值", "基础值"};
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
                        // 日期列与序号列都用数值写（日期为 yyyyMMdd 整数）
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
            return new MockMultipartFile("file", "target-plan.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    bos.toByteArray());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
