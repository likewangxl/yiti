package com.bank.branch.platform.performance.service.importer;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import com.bank.branch.platform.performance.service.SysControlService;
import com.bank.branch.platform.performance.service.importer.impl.MetricResultImportStrategy;
import com.bank.branch.platform.portal.api.AddressBookApi;
import com.bank.branch.platform.portal.api.dto.EmployeeDTO;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetricResultImportStrategy 单元测试（V1.12）.
 *
 * <p>覆盖：
 * <ul>
 *   <li>importType 返回 METRIC_RESULT</li>
 *   <li>Sheet 名作 dataDate（yyyy-MM-dd / yyyyMMdd）</li>
 *   <li>EMP/ORG/CUST/null 4 种 baseDim 路由</li>
 *   <li>baseDim 非法（4.a 违反）/ 指标名称不存在（4.b 违反）
 *       / EMP 不存在 ADDRBOOK_EMPLOYEE（4.c）/ ORG 不存在 EXT_ORG_INFO（4.d）→ errorSummary 记录</li>
 *   <li>baseDim=null 通过校验但不入宽表</li>
 *   <li>UPSERT 携带 val_slot</li>
 * </ul>
 */
class MetricResultImportStrategyTest {

    private static final String SHEET_DATE = "2026-05-19";

    private PerfMetricDefMapper metricDefMapper;
    private EmpIndexResultMapper empMapper;
    private OrgIndexResultMapper orgMapper;
    private CustIndexResultMapper custMapper;
    private AddressBookApi addressBookApi;
    private OrgApi orgApi;
    private SysControlService sysControlService;
    private MetricResultImportStrategy strategy;
    private PerfImportBatch batch;

    @BeforeEach
    void setUp() {
        metricDefMapper = mock(PerfMetricDefMapper.class);
        empMapper = mock(EmpIndexResultMapper.class);
        orgMapper = mock(OrgIndexResultMapper.class);
        custMapper = mock(CustIndexResultMapper.class);
        addressBookApi = mock(AddressBookApi.class);
        orgApi = mock(OrgApi.class);
        sysControlService = mock(SysControlService.class);
        strategy = new MetricResultImportStrategy(
                metricDefMapper, empMapper, orgMapper, custMapper,
                addressBookApi, orgApi, sysControlService);

        // PerfMetricDef 模拟：基础性存款月均余额 → slot 1，年日均余额 → slot 2，CUST 余额 → slot 3
        lenient().when(metricDefMapper.selectByMetricNames(anyList()))
                .thenAnswer(inv -> {
                    List<String> names = inv.getArgument(0);
                    List<PerfMetricDef> defs = new ArrayList<>();
                    for (String n : names) {
                        if ("基础性存款月均余额".equals(n)) {
                            defs.add(metricDef("M_0001", n, 1));
                        } else if ("基础性存款年日均余额".equals(n)) {
                            defs.add(metricDef("M_0002", n, 2));
                        } else if ("客户余额".equals(n)) {
                            defs.add(metricDef("M_0003", n, 3));
                        }
                    }
                    return defs;
                });

        // 员工 / 机构存在性默认放行（具体 case 再覆盖）
        lenient().when(addressBookApi.getEmployee(anyString()))
                .thenReturn(Optional.of(EmployeeDTO.builder().empId("ANY").build()));
        lenient().when(orgApi.getOrg(anyString())).thenReturn(new OrgDTO());
        lenient().when(sysControlService.getCurrentVersion(anyString()))
                .thenReturn(sysControl("V1"));

        batch = new PerfImportBatch();
        batch.setId("BATCH_MR_001");
        batch.setBatchNo("IMP_MR_001");
        batch.setImportType("METRIC_RESULT");
        batch.setCreatedBy("admin");
    }

    @Test
    @DisplayName("importType 返回 METRIC_RESULT")
    void importType_returnsMetricResult() {
        assertThat(strategy.importType()).isEqualTo("METRIC_RESULT");
    }

    @Test
    @DisplayName("execute：EMP/ORG/CUST 三行各 1 → 三 Mapper 各调 1 次，slot 正确")
    void execute_routesByBaseDim_eachMapperOnce() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "EMP", "E001", "基础性存款月均余额", new BigDecimal("10.5")});
        rows.add(new Object[]{2, "ORG", "O001", "基础性存款年日均余额", new BigDecimal("100")});
        rows.add(new Object[]{3, "CUST", "C001", "客户余额", new BigDecimal("999.99")});

        MultipartFile file = writeExcel(SHEET_DATE, rows);
        ImportResult result = strategy.execute(batch, file);

        assertThat(result.getTotalRows()).isEqualTo(3);
        assertThat(result.getSuccessRows()).isEqualTo(3);
        assertThat(result.getErrorRows()).isZero();
        assertThat(result.getErrorSummary()).isNullOrEmpty();

        LocalDate dt = LocalDate.parse(SHEET_DATE);
        verify(empMapper, times(1)).insertSlotValue(eq("E001"), eq(dt),
                eq("V1"), eq(1), any(BigDecimal.class));
        verify(orgMapper, times(1)).insertSlotValue(eq("O001"), eq(dt),
                eq("V1"), eq(2), any(BigDecimal.class));
        verify(custMapper, times(1)).insertSlotValue(eq("C001"), eq(dt),
                eq("V1"), eq(3), any(BigDecimal.class));
    }

    @Test
    @DisplayName("execute：baseDim=null（不区分维度）→ 校验通过但不入任何宽表")
    void execute_nullBaseDim_passesValidationButNotInserted() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "", "", "基础性存款月均余额", new BigDecimal("88")});

        MultipartFile file = writeExcel(SHEET_DATE, rows);
        ImportResult result = strategy.execute(batch, file);

        assertThat(result.getSuccessRows()).isEqualTo(1);
        assertThat(result.getErrorRows()).isZero();
        verify(empMapper, never()).insertSlotValue(any(), any(), any(), any(Integer.class), any());
        verify(orgMapper, never()).insertSlotValue(any(), any(), any(), any(Integer.class), any());
        verify(custMapper, never()).insertSlotValue(any(), any(), any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("4.a 违反：baseDim 非法（XYZ）→ errorSummary 记录，不入库")
    void execute_invalidBaseDim_recordedInErrorSummary() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "XYZ", "X001", "基础性存款月均余额", new BigDecimal("1")});

        MultipartFile file = writeExcel(SHEET_DATE, rows);
        ImportResult result = strategy.execute(batch, file);

        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getErrorSummary()).contains("基础维度非法").contains("XYZ");
        verify(empMapper, never()).insertSlotValue(any(), any(), any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("4.b 违反：指标名称不存在 → METRIC_NOT_FOUND 写入 errorSummary")
    void execute_unknownMetricName_recordedInErrorSummary() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "EMP", "E001", "不存在的指标名", new BigDecimal("1")});

        MultipartFile file = writeExcel(SHEET_DATE, rows);
        ImportResult result = strategy.execute(batch, file);

        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getErrorSummary())
                .contains("指标不存在").contains("不存在的指标名");
        verify(empMapper, never()).insertSlotValue(any(), any(), any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("4.c 违反：EMP baseDim 员工号不在 ADDRBOOK_EMPLOYEE → errorSummary 记录，不入库")
    void execute_empNotFound_recordedInErrorSummary() {
        when(addressBookApi.getEmployee("E_GHOST")).thenReturn(Optional.empty());

        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "EMP", "E_GHOST", "基础性存款月均余额", new BigDecimal("10")});

        MultipartFile file = writeExcel(SHEET_DATE, rows);
        ImportResult result = strategy.execute(batch, file);

        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getErrorSummary()).contains("员工不存在").contains("E_GHOST");
        verify(empMapper, never()).insertSlotValue(any(), any(), any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("4.d 违反：ORG baseDim 机构号不在 EXT_ORG_INFO → errorSummary 记录，不入库")
    void execute_orgNotFound_recordedInErrorSummary() {
        when(orgApi.getOrg("O_GHOST")).thenReturn(null);

        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "ORG", "O_GHOST", "基础性存款年日均余额", new BigDecimal("10")});

        MultipartFile file = writeExcel(SHEET_DATE, rows);
        ImportResult result = strategy.execute(batch, file);

        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getErrorSummary()).contains("机构不存在").contains("O_GHOST");
        verify(orgMapper, never()).insertSlotValue(any(), any(), any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("execute：sys_control 抛 VERSION_NOT_FOUND → 降级 V1，导入仍成功")
    void execute_sysControlMissing_fallbackToV1() {
        when(sysControlService.getCurrentVersion("EMP"))
                .thenThrow(new PerfException(PerfErrorCode.SYS_CONTROL_VERSION_NOT_FOUND, "EMP"));

        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "EMP", "E001", "基础性存款月均余额", new BigDecimal("1")});

        MultipartFile file = writeExcel(SHEET_DATE, rows);
        ImportResult result = strategy.execute(batch, file);

        assertThat(result.getSuccessRows()).isEqualTo(1);
        verify(empMapper, times(1)).insertSlotValue(eq("E001"), any(LocalDate.class),
                eq("V1"), eq(1), any(BigDecimal.class));
    }

    @Test
    @DisplayName("Sheet 名 yyyyMMdd 也能解析为 dataDate")
    void execute_sheetNameYyyymmdd_isParsed() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "EMP", "E001", "基础性存款月均余额", new BigDecimal("1")});

        MultipartFile file = writeExcel("20260519", rows);
        ImportResult result = strategy.execute(batch, file);

        assertThat(result.getSuccessRows()).isEqualTo(1);
        verify(empMapper, times(1)).insertSlotValue(eq("E001"),
                eq(LocalDate.of(2026, 5, 19)), eq("V1"), eq(1), any(BigDecimal.class));
    }

    @Test
    @DisplayName("Sheet 名不可解析为日期 → 行级错误，errorSummary 记录")
    void execute_sheetNameNotParseable_recordedInErrorSummary() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "EMP", "E001", "基础性存款月均余额", new BigDecimal("1")});

        MultipartFile file = writeExcel("非日期Sheet", rows);
        ImportResult result = strategy.execute(batch, file);

        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getErrorSummary()).contains("数据日期");
    }

    @Test
    @DisplayName("多 Sheet：两个日期 Sheet 各 1 行 → 都被处理")
    void execute_multipleSheets_eachProcessed() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            writeOneSheet(wb, "2026-05-18",
                    java.util.Collections.singletonList(
                            new Object[]{1, "EMP", "E001", "基础性存款月均余额", new BigDecimal("1")}));
            writeOneSheet(wb, "2026-05-19",
                    java.util.Collections.singletonList(
                            new Object[]{1, "ORG", "O001", "基础性存款年日均余额", new BigDecimal("2")}));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            MultipartFile file = new MockMultipartFile("file", "multi.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    bos.toByteArray());

            ImportResult result = strategy.execute(batch, file);

            assertThat(result.getTotalRows()).isEqualTo(2);
            assertThat(result.getSuccessRows()).isEqualTo(2);
            verify(empMapper, times(1)).insertSlotValue(eq("E001"),
                    eq(LocalDate.of(2026, 5, 18)), eq("V1"), eq(1), any(BigDecimal.class));
            verify(orgMapper, times(1)).insertSlotValue(eq("O001"),
                    eq(LocalDate.of(2026, 5, 19)), eq("V1"), eq(2), any(BigDecimal.class));
        }
    }

    // ================ helpers ================

    private static PerfMetricDef metricDef(String code, String name, int slot) {
        PerfMetricDef d = new PerfMetricDef();
        d.setMetricCode(code);
        d.setMetricName(name);
        d.setValSlot(slot);
        d.setStatus("ACTIVE");
        return d;
    }

    private static SysControl sysControl(String version) {
        SysControl sc = new SysControl();
        sc.setCurrentVersion(version);
        sc.setIsValid(1);
        return sc;
    }

    /** 单 sheet 文件：表头 5 列 + 给定数据行，sheet 名 = dataDate. */
    private static MultipartFile writeExcel(String sheetName, List<Object[]> rows) {
        try (Workbook wb = new XSSFWorkbook()) {
            writeOneSheet(wb, sheetName, rows);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return new MockMultipartFile("file", "metric-result.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    bos.toByteArray());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** 在 wb 上追加一个 sheet：表头 5 列 + 给定数据行. */
    private static void writeOneSheet(Workbook wb, String sheetName, List<Object[]> rows) {
        Sheet sheet = wb.createSheet(sheetName);
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("序号");
        header.createCell(1).setCellValue("基础维度");
        header.createCell(2).setCellValue("维度对象");
        header.createCell(3).setCellValue("指标名称");
        header.createCell(4).setCellValue("指标数值");
        for (int i = 0; i < rows.size(); i++) {
            Row r = sheet.createRow(i + 1);
            Object[] cells = rows.get(i);
            // 序号
            if (cells[0] != null) {
                r.createCell(0).setCellValue(String.valueOf(cells[0]));
            }
            // 基础维度
            if (cells[1] != null) {
                r.createCell(1).setCellValue(String.valueOf(cells[1]));
            }
            // 维度对象
            if (cells[2] != null) {
                r.createCell(2).setCellValue(String.valueOf(cells[2]));
            }
            // 指标名称
            if (cells[3] != null) {
                r.createCell(3).setCellValue(String.valueOf(cells[3]));
            }
            // 指标数值
            if (cells[4] != null) {
                BigDecimal v = (BigDecimal) cells[4];
                r.createCell(4).setCellValue(v.doubleValue());
            }
        }
    }
}
