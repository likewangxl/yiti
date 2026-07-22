package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.storage.FileCategory;
import com.bank.branch.platform.report.entity.RptFreeReportBatch;
import com.bank.branch.platform.report.entity.RptFreeReportRow;
import com.bank.branch.platform.report.mapper.FreeReportBatchMapper;
import com.bank.branch.platform.report.mapper.FreeReportRowMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FreeReportServiceImpl 单测：自由报表导入。
 * 重点验证「同名同上传人覆盖再导入」不再清理旧文件（避免清理时 deleteFile 对已去重共享/已删记录
 * 抛异常污染 @Transactional 导入事务导致整单回滚），且上传带 zybb 类型前缀。
 */
@ExtendWith(MockitoExtension.class)
class FreeReportServiceImplTest {

    @Mock FreeReportBatchMapper batchMapper;
    @Mock FreeReportRowMapper rowMapper;
    @Mock FileApi fileApi;

    FreeReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FreeReportServiceImpl(batchMapper, rowMapper, fileApi, new ObjectMapper());
    }

    /** 构造最小合法 xlsx：表头[工号,姓名] + 一行数据。 */
    private MultipartFile xlsx(String name) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("s");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("工号");
            h.createCell(1).setCellValue("姓名");
            Row d = s.createRow(1);
            d.createCell(0).setCellValue("E1");
            d.createCell(1).setCellValue("张三");
            wb.write(out);
            return new MockMultipartFile("file", name,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    /**
     * 构造带「数字格式」的 xlsx，复刻用户报的两种现象：
     *   col_3：值 -5.00000000069889E-7，格式 0.0   -> Excel 显示 -0.0
     *   col_4：值 0.545175438596492，  格式 0.0%  -> Excel 显示 54.5%
     */
    private MultipartFile xlsxWithFormats() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("s");
            org.apache.poi.ss.usermodel.CellStyle st1 = wb.createCellStyle();
            st1.setDataFormat(wb.createDataFormat().getFormat("0.0"));
            org.apache.poi.ss.usermodel.CellStyle st2 = wb.createCellStyle();
            st2.setDataFormat(wb.createDataFormat().getFormat("0.0%"));

            Row h = s.createRow(0);
            h.createCell(0).setCellValue("工号");
            h.createCell(1).setCellValue("姓名");
            h.createCell(2).setCellValue("微小值");
            h.createCell(3).setCellValue("占比");

            Row d = s.createRow(1);
            d.createCell(0).setCellValue("E1");
            d.createCell(1).setCellValue("张三");
            Cell c3 = d.createCell(2);
            c3.setCellValue(-5.00000000069889E-7);
            c3.setCellStyle(st1);
            Cell c4 = d.createCell(3);
            c4.setCellValue(0.545175438596492);
            c4.setCellStyle(st2);

            wb.write(out);
            return new MockMultipartFile("file", "fmt.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    /** 会计格式（0 显示为 "-"），注意结尾的空格是格式的一部分，不能裁掉。 */
    private static final String ACCT_FMT = "_ * #,##0_ ;_ * \\-#,##0_ ;_ * \"-\"_ ;_ @_ ";
    /** 自定义格式（负数标红），结尾是「反斜杠 + 空格」，裁掉空格会让反斜杠变成孤立转义符。 */
    private static final String CUST_FMT = "0.00_ ;[Red]\\-0.00\\ ";

    /**
     * 数字格式串必须**原样**保留，尤其不能 trim 掉尾部空格。
     *
     * <p>Excel 格式里 `_`(占位一个字符宽) 与 `\`(转义) 后面都必须跟一个字符。
     * 会计格式 `..._ @_ ` 裁掉尾空格后成 `..._ @_`（孤立下划线）；
     * 自定义格式 `...\-0.00\ ` 裁掉后成 `...\-0.00\`（孤立反斜杠）——
     * Excel/WPS 判定整串非法后会**退回常规格式**，于是 0 显示成 "0"、极小值显示成科学计数法，
     * 正是用户反馈的「下载后格子显示 0、编辑栏也是 0」。
     */
    @Test
    void importExcel_keepsFormatStringVerbatim_noTrimming() throws Exception {
        FileObjectDTO dto = new FileObjectDTO();
        dto.setId("F1");
        when(fileApi.upload(any(MultipartFile.class), eq("E1"), eq(FileCategory.FREE_REPORT))).thenReturn(dto);

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("s");
            org.apache.poi.ss.usermodel.CellStyle acct = wb.createCellStyle();
            acct.setDataFormat(wb.createDataFormat().getFormat(ACCT_FMT));
            org.apache.poi.ss.usermodel.CellStyle cust = wb.createCellStyle();
            cust.setDataFormat(wb.createDataFormat().getFormat(CUST_FMT));

            Row h = s.createRow(0);
            h.createCell(0).setCellValue("工号");
            h.createCell(1).setCellValue("姓名");
            h.createCell(2).setCellValue("会计零");
            h.createCell(3).setCellValue("极小值");
            Row d = s.createRow(1);
            d.createCell(0).setCellValue("E1");
            d.createCell(1).setCellValue("张三");
            Cell c3 = d.createCell(2); c3.setCellValue(0); c3.setCellStyle(acct);
            Cell c4 = d.createCell(3); c4.setCellValue(-5.00000000069889E-07); c4.setCellStyle(cust);
            wb.write(out);

            service.importExcel("rpt", new MockMultipartFile("file", "f.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray()), "E1", "张三");
        }

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<RptFreeReportRow>> cap =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(rowMapper).insertBatch(cap.capture());
        String json = cap.getValue().get(0).getDataJson();

        // 反序列化后逐字比对格式串，必须与源格式**完全一致**（含尾空格）
        Map<String, String> m = new ObjectMapper().readValue(json, new TypeReference<>() {});
        assertThat(m.get("col_3__fmt")).isEqualTo(ACCT_FMT);
        assertThat(m.get("col_4__fmt")).isEqualTo(CUST_FMT);
        // 尾字符必须还是空格——被 trim 掉就会让 Excel 判非法
        assertThat(m.get("col_3__fmt")).endsWith(" ");
        assertThat(m.get("col_4__fmt")).endsWith(" ");
    }

    /**
     * 会计格式下，按格式舍入后为零的极小值必须走「零段」显示为 "-"，与 Excel 一致。
     *
     * <p>Excel 决定用格式的哪一段（正;负;零）是看**按该格式舍入后**的值：
     * `#,##0` 把 -5e-7 舍成 0，故走第三段显示 "-"。但 POI 的 DataFormatter 只看原始值符号，
     * 直接走负数段渲染成 "-0" —— 正是用户反馈的「页面展示的 -0」。
     */
    @Test
    void importExcel_accountingFormat_tinyValueRoundsToZero_showsDash() throws Exception {
        FileObjectDTO dto = new FileObjectDTO();
        dto.setId("F1");
        when(fileApi.upload(any(MultipartFile.class), eq("E1"), eq(FileCategory.FREE_REPORT))).thenReturn(dto);

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("s");
            org.apache.poi.ss.usermodel.CellStyle acct = wb.createCellStyle();
            acct.setDataFormat(wb.createDataFormat().getFormat(ACCT_FMT));

            Row h = s.createRow(0);
            h.createCell(0).setCellValue("工号");
            h.createCell(1).setCellValue("姓名");
            h.createCell(2).setCellValue("会计-精确零");
            h.createCell(3).setCellValue("会计-极小负值");
            Row d = s.createRow(1);
            d.createCell(0).setCellValue("E1");
            d.createCell(1).setCellValue("张三");
            Cell c3 = d.createCell(2); c3.setCellValue(0); c3.setCellStyle(acct);
            Cell c4 = d.createCell(3); c4.setCellValue(-5.00000000069889E-07); c4.setCellStyle(acct);
            wb.write(out);

            service.importExcel("rpt", new MockMultipartFile("file", "f.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray()), "E1", "张三");
        }

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<RptFreeReportRow>> cap =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(rowMapper).insertBatch(cap.capture());
        Map<String, String> m = new ObjectMapper()
                .readValue(cap.getValue().get(0).getDataJson(), new TypeReference<>() {});

        // 精确零：本来就正确
        assertThat(m.get("col_3")).isEqualTo("-");
        // 极小负值：舍入后为零，同样应显示 "-"，不能是 "-0"
        assertThat(m.get("col_4")).isEqualTo("-");
        // 完整原值仍保留，点击可见
        assertThat(m.get("col_4__raw")).isEqualTo("-0.000000500000000069889");
    }

    /**
     * 真实报表回归固件：{@code src/test/resources/freereport/real-formats.xlsx}
     * 取自现场问题文件，含三类真实格式——内置 0.00 / 0% / 0.00%、会计格式(numFmtId=41)、
     * 自定义 {@code 0.00_ ;[Red]\-0.00\ }(numFmtId=177)。
     *
     * <p>此前用代码新建 workbook 构造的用例复现不出问题：新建自定义格式 POI 分配 numFmtId=164
     * 时渲染正常，而**从文件读入**的 numFmtId=177 会让 {@code formatCellValue(cell)} 退化成
     * 科学计数法（-5.00000000069889E-07）。必须用真实文件才能守住这条回归。
     */
    @Test
    void importExcel_realFile_noScientificNotation_andAccountingDash() throws Exception {
        FileObjectDTO dto = new FileObjectDTO();
        dto.setId("F1");
        when(fileApi.upload(any(MultipartFile.class), eq("E1"), eq(FileCategory.FREE_REPORT))).thenReturn(dto);

        byte[] src;
        try (java.io.InputStream in = getClass().getResourceAsStream("/freereport/real-formats.xlsx")) {
            assertThat(in).as("回归固件 real-formats.xlsx 必须存在").isNotNull();
            src = in.readAllBytes();
        }
        service.importExcel("rpt", new MockMultipartFile("file", "real.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", src), "E1", "张三");

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<RptFreeReportRow>> cap =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(rowMapper, org.mockito.Mockito.atLeastOnce()).insertBatch(cap.capture());
        List<RptFreeReportRow> rows = cap.getAllValues().get(0);
        ObjectMapper om = new ObjectMapper();

        // 全表任何一格的显示文本都不许出现科学计数法
        for (RptFreeReportRow r : rows) {
            Map<String, String> m = om.readValue(r.getDataJson(), new TypeReference<>() {});
            for (Map.Entry<String, String> e : m.entrySet()) {
                if (e.getKey().endsWith("__raw") || e.getKey().endsWith("__fmt")) continue;
                assertThat(e.getValue())
                        .as("显示文本不得含科学计数法: %s=%s", e.getKey(), e.getValue())
                        .doesNotMatch(".*\\d[eE][+-]?\\d.*");
            }
        }

        // 第3行(会计格式 0) 显示 "-"；第4行(自定义格式 极小值) 显示 -0.00 且保留完整原值
        Map<String, String> r3 = om.readValue(rows.get(2).getDataJson(), new TypeReference<>() {});
        assertThat(r3.get("col_9")).isEqualTo("-");
        assertThat(r3.get("col_9__raw")).isEqualTo("0.0");

        Map<String, String> r4 = om.readValue(rows.get(3).getDataJson(), new TypeReference<>() {});
        assertThat(r4.get("col_9")).isEqualTo("-0.00");
        assertThat(r4.get("col_9__raw")).isEqualTo("-0.000000500000000069889");
    }

    /**
     * 导入必须保留 Excel 的「显示文本」与「数字格式」，而不是把原始值 String.valueOf 成科学计数法。
     * 存三份：col_N=显示文本(页面用) / col_N__raw=完整原值(点击看) / col_N__fmt=数字格式(导出复刻用)。
     */
    @Test
    void importExcel_keepsDisplayTextRawValueAndFormat() throws Exception {
        FileObjectDTO dto = new FileObjectDTO();
        dto.setId("F1");
        when(fileApi.upload(any(MultipartFile.class), eq("E1"), eq(FileCategory.FREE_REPORT))).thenReturn(dto);

        service.importExcel("rpt", xlsxWithFormats(), "E1", "张三");

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<RptFreeReportRow>> cap =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(rowMapper).insertBatch(cap.capture());
        String json = cap.getValue().get(0).getDataJson();

        // 微小值：页面显示 -0.0，不是科学计数法
        assertThat(json).contains("\"col_3\":\"-0.0\"");
        assertThat(json).doesNotContain("E-7");          // 显示文本里不许出现科学计数法
        assertThat(json).contains("\"col_3__raw\":");    // 完整原值另存
        assertThat(json).contains("\"col_3__fmt\":\"0.0\"");

        // 百分比：页面显示 54.5%
        assertThat(json).contains("\"col_4\":\"54.5%\"");
        assertThat(json).contains("\"col_4__raw\":");
        assertThat(json).contains("\"col_4__fmt\":\"0.0%\"");
    }

    @Test
    void reimportSameName_doesNotCleanupOldFile_andUsesZybbCategory() throws Exception {
        // 已有同名、同上传人的旧批次（覆盖场景）
        RptFreeReportBatch old = new RptFreeReportBatch();
        old.setId("B_OLD");
        old.setUploaderEmpId("E1");
        old.setFileName("r.xlsx");
        old.setFileObjectKey("F_OLD");
        when(batchMapper.selectByFileName("r.xlsx")).thenReturn(List.of(old));

        FileObjectDTO dto = new FileObjectDTO();
        dto.setId("F_NEW");
        when(fileApi.upload(any(MultipartFile.class), eq("E1"), eq(FileCategory.FREE_REPORT))).thenReturn(dto);

        String batchId = service.importExcel("rpt", xlsx("r.xlsx"), "E1", "张三");

        assertThat(batchId).isNotBlank();
        // 覆盖：删旧批次行 + 旧批次记录
        verify(rowMapper).deleteByBatchId("B_OLD");
        verify(batchMapper).deleteById("B_OLD");
        // 不再清理旧 OBS 文件（否则会污染导入事务）
        verify(fileApi, never()).deleteFile(anyString());
        // 自由报表用 zybb 类型前缀
        verify(fileApi).upload(any(MultipartFile.class), eq("E1"), eq(FileCategory.FREE_REPORT));
    }

    /**
     * 下载导出：单元格存**完整原值** + 0.00 格式 —— 点击该格时编辑栏干净地显示 3.1779998。
     * 代价是格内按 0.00 四舍五入显示（3.18），这是用户 2026-07-20 明确取舍的结果：
     * Excel/WPS 数字格式只会四舍五入不会截断，「编辑栏干净显示完整值」与「格内截断显示」不可共存。
     */
    @Test
    void exportFilteredExcel_longDecimal_keepsFullValueInCell() throws Exception {
        RptFreeReportBatch batch = new RptFreeReportBatch();
        batch.setId("B1");
        batch.setColDefs("[{\"key\":\"col_1\",\"label\":\"工号\"},{\"key\":\"col_2\",\"label\":\"姓名\"},{\"key\":\"col_3\",\"label\":\"金额\"}]");
        when(batchMapper.selectById("B1")).thenReturn(batch);

        RptFreeReportRow row = new RptFreeReportRow();
        row.setCol1("E1");
        row.setCol2("张三");
        row.setDataJson("{\"col_3\":\"3.1779998\"}");
        when(rowMapper.countByBatch(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1L);
        when(rowMapper.selectByBatch(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(row));

        byte[] bytes = service.exportFilteredExcel("B1", "ALL", null, null, null);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            Row data = sheet.getRow(1);
            // 工号/姓名 原样文本
            assertThat(data.getCell(0).getStringCellValue()).isEqualTo("E1");
            assertThat(data.getCell(1).getStringCellValue()).isEqualTo("张三");
            Cell amt = data.getCell(2);
            // 纯数值格、无公式壳：点击格子 → 编辑栏就是 3.1779998
            assertThat(amt.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(amt.getNumericCellValue()).isCloseTo(3.1779998, within(1e-9));
            assertThat(amt.getCellStyle().getDataFormatString()).isEqualTo("0.00");
            // 既不挂数据有效性弹框，也不写公式
            assertThat(sheet.getDataValidations()).isEmpty();
        }
    }

    /**
     * 下载导出：数值格写「完整原值 + 原 Excel 数字格式」，完整复刻原表行为——
     * 格内显示 -0.0 / 54.5%，点击后编辑栏是完整值。不再出现科学计数法。
     */
    @Test
    void exportFilteredExcel_usesOriginalFormatAndRawValue() throws Exception {
        RptFreeReportBatch batch = new RptFreeReportBatch();
        batch.setId("B1");
        batch.setColDefs("[{\"key\":\"col_1\",\"label\":\"工号\"},{\"key\":\"col_3\",\"label\":\"微小值\"}"
                + ",{\"key\":\"col_4\",\"label\":\"占比\"}]");
        when(batchMapper.selectById("B1")).thenReturn(batch);

        RptFreeReportRow row = new RptFreeReportRow();
        row.setCol1("E1");
        // 导入后落库的形态：显示文本 + 完整原值 + 原格式
        row.setDataJson("{\"col_3\":\"-0.0\",\"col_3__raw\":\"-0.000000500000000069889\",\"col_3__fmt\":\"0.0\","
                + "\"col_4\":\"54.5%\",\"col_4__raw\":\"0.545175438596492\",\"col_4__fmt\":\"0.0%\"}");
        when(rowMapper.countByBatch(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1L);
        when(rowMapper.selectByBatch(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(row));

        byte[] bytes = service.exportFilteredExcel("B1", "ALL", null, null, null);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row data = wb.getSheetAt(0).getRow(1);

            Cell tiny = data.getCell(1);
            assertThat(tiny.getCellType()).isEqualTo(CellType.NUMERIC);
            // 单元格里是完整原值（点击编辑栏可见），不是显示文本
            assertThat(tiny.getNumericCellValue()).isCloseTo(-5.00000000069889E-7, within(1e-20));
            // 套原格式 0.0 -> Excel 渲染成 -0.0
            assertThat(tiny.getCellStyle().getDataFormatString()).isEqualTo("0.0");

            Cell pct = data.getCell(2);
            assertThat(pct.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(pct.getNumericCellValue()).isCloseTo(0.545175438596492, within(1e-15));
            assertThat(pct.getCellStyle().getDataFormatString()).isEqualTo("0.0%");
        }
    }

    /** 小数位≤2：同样是纯数值格 + 0.00，显示与原值一致。 */
    @Test
    void exportFilteredExcel_shortDecimal_writesPlainNumber() throws Exception {
        RptFreeReportBatch batch = new RptFreeReportBatch();
        batch.setId("B1");
        batch.setColDefs("[{\"key\":\"col_1\",\"label\":\"工号\"},{\"key\":\"col_3\",\"label\":\"金额\"}]");
        when(batchMapper.selectById("B1")).thenReturn(batch);

        RptFreeReportRow row = new RptFreeReportRow();
        row.setCol1("E1");
        row.setDataJson("{\"col_3\":\"3.1\"}");
        when(rowMapper.countByBatch(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1L);
        when(rowMapper.selectByBatch(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(row));

        byte[] bytes = service.exportFilteredExcel("B1", "ALL", null, null, null);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Cell amt = wb.getSheetAt(0).getRow(1).getCell(1);
            assertThat(amt.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(amt.getNumericCellValue()).isCloseTo(3.1, within(1e-9));
            assertThat(amt.getCellStyle().getDataFormatString()).isEqualTo("0.00");
        }
    }

    /** 数值格判定：只有纯小数走数值格；整数/文本走文本原样写（防工号、编号类被转成数值）。 */
    @Test
    void isDecimal_rule() {
        assertThat(FreeReportServiceImpl.isDecimal("3.1779998")).isTrue();
        assertThat(FreeReportServiceImpl.isDecimal("-2.999")).isTrue();
        assertThat(FreeReportServiceImpl.isDecimal("1001")).isFalse();
        assertThat(FreeReportServiceImpl.isDecimal("张三")).isFalse();
        assertThat(FreeReportServiceImpl.isDecimal("")).isFalse();
        assertThat(FreeReportServiceImpl.isDecimal(null)).isFalse();
    }
}
