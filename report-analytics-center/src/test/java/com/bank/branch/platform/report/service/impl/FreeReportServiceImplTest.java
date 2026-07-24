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
     * 分段格式选段看的是**原始值的符号**，不是按格式舍入后的值。
     *
     * <p>曾经反过来假设（以为 `#,##0` 把 -5e-7 舍成 0 就该走零段显示 "-"），并据此加了
     * 一个「舍入为零就改用零段重渲染」的修正。**这个假设是错的**，现场报表两次证伪：
     * 四段会计格式 `_ * #,##0.00_ ;_ * \-#,##0.00_ ;_ * "-"??_ ;_ @_ ` 下，
     * -5.00000000069889E-07 在 Excel 里显示的是 **-0.00**（负数段），不是 "-"。
     * 那个修正反而把对的改错，是用户所见 "-0" 的直接来源，已删除。
     *
     * <p>故本用例的口径：精确零走零段显示 "-"；极小负值走负数段，整数会计格式下就是 "-0"。
     */
    @Test
    void importExcel_accountingFormat_zeroShowsDash_tinyNegativeKeepsNegativeSection() throws Exception {
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

        // 精确零：走零段，显示 "-"
        assertThat(m.get("col_3")).isEqualTo("-");
        // 极小负值：值本身是负的，走**负数段** `_ * \-#,##0_ `，整数格式下就是 "-0"。
        // 不能"顺手"改成零段的 "-"——现场报表已证实 Excel 就是这么显示的。
        assertThat(m.get("col_4")).isEqualTo("-0");
        // 完整原值仍保留，且与 Excel 编辑栏一致（编辑栏本就是科学计数法）
        assertThat(m.get("col_4__raw")).isEqualTo("-5.00000000069889E-07");
    }

    /** 现场报表的真实会计格式——零段是 `"-"??`（带问号占位符），整数版。 */
    private static final String ACCT_QM_INT = "_ * #,##0_ ;_ * \\-#,##0_ ;_ * \"-\"??_ ;_ @_ ";
    /** 同上，两位小数版。 */
    private static final String ACCT_QM_DEC = "_ * #,##0.00_ ;_ * \\-#,##0.00_ ;_ * \"-\"??_ ;_ @_ ";

    /**
     * 会计格式零段里的 `??` 是**空格占位符**，不是数字占位符——POI 会多渲染出一个 0。
     *
     * <p>现场报表两处错显都由它引起（用户 2026-07-24 反馈，附 DATA_JSON 实据）：
     * <pre>
     *   col_20  fmt=_ * #,##0_ ;_ * \-#,##0_ ;_ * "-"??_ ;_ @_    值 0      Excel 显示 "-"     页面却是 "- 0"
     *   col_124 fmt=_ * #,##0.00_ ;...;_ * "-"??_ ;_ @_           值 -5e-7  Excel 显示 "-0.00" 页面却是 "-0"
     * </pre>
     *
     * <p>Excel 语义：`0` 是强制数字位（无值也显示 0），`#` 是可选数字位（无值不显示），
     * `?` 是**对齐用的空格位**（无值显示空格）。零段 `_ * "-"??_ ` 里根本没有 `0`，
     * 所以值为 0 时只该显示 "-" 加两个对齐空格；POI 把 `?` 当成了数字位，渲染成 "- 0"。
     *
     * <p>第二处 "-0" 则是另一个 Bug：极小负值 POI 本来就正确渲染为 "-0.00"（负数段），
     * 是曾经那个「舍入为零改走零段」的修正把它改成了零段的 "- 0"。该修正已删除。
     */
    @Test
    void importExcel_accountingZeroSectionWithQuestionMark_showsBareDash() throws Exception {
        FileObjectDTO dto = new FileObjectDTO();
        dto.setId("F1");
        when(fileApi.upload(any(MultipartFile.class), eq("E1"), eq(FileCategory.FREE_REPORT))).thenReturn(dto);

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("s");
            org.apache.poi.ss.usermodel.CellStyle qmInt = wb.createCellStyle();
            qmInt.setDataFormat(wb.createDataFormat().getFormat(ACCT_QM_INT));
            org.apache.poi.ss.usermodel.CellStyle qmDec = wb.createCellStyle();
            qmDec.setDataFormat(wb.createDataFormat().getFormat(ACCT_QM_DEC));

            Row h = s.createRow(0);
            h.createCell(0).setCellValue("工号");
            h.createCell(1).setCellValue("姓名");
            h.createCell(2).setCellValue("案例1-整数零");
            h.createCell(3).setCellValue("案例2-极小负值");
            h.createCell(4).setCellValue("对照-两位小数零");
            h.createCell(5).setCellValue("对照-正数");
            h.createCell(6).setCellValue("对照-负数");
            Row d = s.createRow(1);
            d.createCell(0).setCellValue("E1");
            d.createCell(1).setCellValue("张三");
            Cell c3 = d.createCell(2); c3.setCellValue(0d);          c3.setCellStyle(qmInt);
            Cell c4 = d.createCell(3); c4.setCellValue(-5.00000000069889E-07); c4.setCellStyle(qmDec);
            Cell c5 = d.createCell(4); c5.setCellValue(0d);          c5.setCellStyle(qmDec);
            Cell c6 = d.createCell(5); c6.setCellValue(1234.5678);   c6.setCellStyle(qmDec);
            Cell c7 = d.createCell(6); c7.setCellValue(-1234.5678);  c7.setCellStyle(qmDec);
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

        // 案例1：零段的 ?? 不该冒出数字 0
        assertThat(m.get("col_3")).isEqualTo("-");
        assertThat(m.get("col_3__raw")).isEqualTo("0");
        // 案例2：极小负值走负数段，两位小数格式下就是 -0.00
        assertThat(m.get("col_4")).isEqualTo("-0.00");
        assertThat(m.get("col_4__raw")).isEqualTo("-5.00000000069889E-07");
        // 对照：两位小数格式的精确零同样只显示 "-"
        assertThat(m.get("col_5")).isEqualTo("-");
        // 对照：正常正负值不受影响，千分位与两位小数都在
        assertThat(m.get("col_6")).isEqualTo("1,234.57");
        assertThat(m.get("col_7")).isEqualTo("-1,234.57");
    }

    /**
     * 导出时 __raw 可能是科学计数法（Excel 编辑栏对极小值的原样表示），
     * 必须仍按**数值**写入并套原格式；若被当成文本写，格子里就成了死字符串，
     * 既不受数字格式控制、点击也看不到原值。
     */
    @Test
    void exportFilteredExcel_scientificNotationRaw_writtenAsNumberWithFormat() throws Exception {
        RptFreeReportBatch batch = new RptFreeReportBatch();
        batch.setId("B1");
        batch.setColDefs("[{\"key\":\"col_1\",\"label\":\"工号\"},{\"key\":\"col_9\",\"label\":\"极小值\"}]");
        when(batchMapper.selectById("B1")).thenReturn(batch);

        RptFreeReportRow row = new RptFreeReportRow();
        row.setCol1("E1");
        row.setDataJson("{\"col_9\":\"-0.00\",\"col_9__raw\":\"-5.00000000069889E-07\","
                + "\"col_9__fmt\":\"0.00_ ;[Red]\\\\-0.00\\\\ \"}");
        when(rowMapper.countByBatch(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1L);
        when(rowMapper.selectByBatch(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(row));

        byte[] bytes = service.exportFilteredExcel("B1", "ALL", null, null, null);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Cell c = wb.getSheetAt(0).getRow(1).getCell(1);
            assertThat(c.getCellType()).as("必须是数值格而非文本").isEqualTo(CellType.NUMERIC);
            assertThat(c.getNumericCellValue()).isCloseTo(-5.00000000069889E-07, within(1e-20));
            assertThat(c.getCellStyle().getDataFormatString()).isEqualTo("0.00_ ;[Red]\\-0.00\\ ");
        }
    }

    /**
     * 全表逐格比对：源 Excel 与「导入→导出」后的 Excel，**每一格**的
     * 「格内显示」和「编辑栏原值」都必须相同。
     *
     * <p>这是本特性的总验收——不是只看有问题的那一列，而是整张表任何一格都不许走样。
     */
    @Test
    void roundTrip_everyCell_displayAndRawValueMatchSource() throws Exception {
        FileObjectDTO dto = new FileObjectDTO();
        dto.setId("F1");
        when(fileApi.upload(any(MultipartFile.class), eq("E1"), eq(FileCategory.FREE_REPORT))).thenReturn(dto);

        byte[] src;
        try (java.io.InputStream in = getClass().getResourceAsStream("/freereport/real-formats.xlsx")) {
            assertThat(in).isNotNull();
            src = in.readAllBytes();
        }
        service.importExcel("rpt", new MockMultipartFile("file", "real.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", src), "E1", "张三");

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<RptFreeReportRow>> cap =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(rowMapper, org.mockito.Mockito.atLeastOnce()).insertBatch(cap.capture());
        List<RptFreeReportRow> rows = cap.getAllValues().get(0);

        // 用源文件的真实表头拼列定义，保证导出列序与源一致
        StringBuilder cd = new StringBuilder("[");
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(src))) {
            Row hr = wb.getSheetAt(0).getRow(0);
            for (int c = 0; c < hr.getLastCellNum(); c++) {
                if (c > 0) cd.append(",");
                cd.append("{\"key\":\"col_").append(c + 1).append("\",\"label\":\"h").append(c).append("\"}");
            }
        }
        RptFreeReportBatch batch = new RptFreeReportBatch();
        batch.setId("B1");
        batch.setColDefs(cd.append("]").toString());
        when(batchMapper.selectById("B1")).thenReturn(batch);
        when(rowMapper.countByBatch(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn((long) rows.size());
        when(rowMapper.selectByBatch(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(rows);

        byte[] out = service.exportFilteredExcel("B1", "ALL", null, null, null);

        try (XSSFWorkbook s1 = new XSSFWorkbook(new ByteArrayInputStream(src));
             XSSFWorkbook s2 = new XSSFWorkbook(new ByteArrayInputStream(out))) {
            Sheet a = s1.getSheetAt(0), b = s2.getSheetAt(0);
            org.apache.poi.ss.usermodel.DataFormatter f = new org.apache.poi.ss.usermodel.DataFormatter();
            int checked = 0;
            for (int r = 1; r <= a.getLastRowNum(); r++) {          // 跳表头
                Row ra = a.getRow(r), rb = b.getRow(r);
                if (ra == null) continue;
                assertThat(rb).as("导出缺行 %d", r + 1).isNotNull();
                for (int c = 0; c < ra.getLastCellNum(); c++) {
                    Cell ca = ra.getCell(c), cb = rb.getCell(c);
                    if (ca == null || ca.getCellType() == CellType.BLANK) continue;
                    assertThat(cb).as("导出缺格 行%d列%d", r + 1, c + 1).isNotNull();

                    // 源文件里可能有公式格（本固件的 I5 就是），其"值类型"要取缓存结果类型；
                    // 导出侧一律落成数值/文本快照，不保留公式（自由报表是数据快照）。
                    CellType typeA = ca.getCellType() == CellType.FORMULA
                            ? ca.getCachedFormulaResultType() : ca.getCellType();
                    if (typeA == CellType.NUMERIC) {
                        assertThat(cb.getCellType())
                                .as("行%d列%d 源是数值，导出必须仍是数值格", r + 1, c + 1)
                                .isEqualTo(CellType.NUMERIC);
                        // 编辑栏原值
                        assertThat(FreeReportServiceImpl.excelRawText(cb.getNumericCellValue()))
                                .as("行%d列%d 编辑栏原值不一致", r + 1, c + 1)
                                .isEqualTo(FreeReportServiceImpl.excelRawText(ca.getNumericCellValue()));
                        // 格内显示（各自按自身格式渲染）
                        String showA = f.formatRawCellContents(ca.getNumericCellValue(),
                                ca.getCellStyle().getDataFormat(), ca.getCellStyle().getDataFormatString()).trim();
                        String showB = f.formatRawCellContents(cb.getNumericCellValue(),
                                cb.getCellStyle().getDataFormat(), cb.getCellStyle().getDataFormatString()).trim();
                        assertThat(showB).as("行%d列%d 格内显示不一致", r + 1, c + 1).isEqualTo(showA);
                    } else {
                        // 文本列：内容原样搬运
                        assertThat(f.formatCellValue(cb).trim())
                                .as("行%d列%d 文本内容不一致", r + 1, c + 1)
                                .isEqualTo(f.formatCellValue(ca).trim());
                    }
                    checked++;
                }
            }
            assertThat(checked).as("应比对到足够多的数值格").isGreaterThanOrEqualTo(15);
        }
    }

    /**
     * 百分比格式的「完整值」必须与 Excel 编辑栏一致——带 % 且已乘 100。
     *
     * <p>Excel 对百分比格式的单元格，编辑栏显示的是 {@code -1683.24723247232%}，
     * 而不是底层存储的小数 {@code -16.8324723247232}。此前直接给底层小数，
     * 页面点开看到的数跟源报表对不上（用户实测：源 -1683.24723247232%，页面 -16.83…）。
     */
    @Test
    void importExcel_percentFormat_rawValueCarriesPercentSign() throws Exception {
        FileObjectDTO dto = new FileObjectDTO();
        dto.setId("F1");
        when(fileApi.upload(any(MultipartFile.class), eq("E1"), eq(FileCategory.FREE_REPORT))).thenReturn(dto);

        byte[] src;
        try (java.io.InputStream in = getClass().getResourceAsStream("/freereport/real-formats.xlsx")) {
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

        // 0% 格式，底层 0.91 -> 编辑栏 91%
        Map<String, String> r1 = om.readValue(rows.get(0).getDataJson(), new TypeReference<>() {});
        assertThat(r1.get("col_8")).isEqualTo("91%");
        assertThat(r1.get("col_8__raw")).isEqualTo("91%");

        // 0.00% 格式，底层 0.498888897666 -> 编辑栏 49.8888897666%（乘100后不得有浮点误差）
        Map<String, String> r2 = om.readValue(rows.get(1).getDataJson(), new TypeReference<>() {});
        assertThat(r2.get("col_8")).isEqualTo("49.89%");
        assertThat(r2.get("col_8__raw")).isEqualTo("49.8888897666%");

        // 非百分比列不受影响
        assertThat(r1.get("col_4__raw")).isEqualTo("4540.69998");
    }

    /** excelRawText：把 double 渲染成与 Excel 编辑栏一字不差的文本。 */
    @Test
    void excelRawText_matchesExcelFormulaBar() {
        // 零：Excel 编辑栏是 "0"，不是 Java 的 "0.0"
        assertThat(FreeReportServiceImpl.excelRawText(0d)).isEqualTo("0");
        assertThat(FreeReportServiceImpl.excelRawText(-0d)).isEqualTo("0");
        // 整数：无 ".0" 尾巴
        assertThat(FreeReportServiceImpl.excelRawText(85d)).isEqualTo("85");
        // 普通小数：完整精度，不被截断
        assertThat(FreeReportServiceImpl.excelRawText(4540.69998)).isEqualTo("4540.69998");
        assertThat(FreeReportServiceImpl.excelRawText(0.498888897666)).isEqualTo("0.498888897666");
        // 极小值：科学计数法且指数补两位带符号（Excel 写法）
        assertThat(FreeReportServiceImpl.excelRawText(-5.00000000069889E-07)).isEqualTo("-5.00000000069889E-07");
        // 阈值内的小数仍平铺，不提前切科学计数法（Java 在 1e-3 就切，Excel 不会）
        assertThat(FreeReportServiceImpl.excelRawText(0.0005)).isEqualTo("0.0005");
        // 大数在阈值内也平铺（Java 在 1e7 就切科学计数法，Excel 编辑栏不会）
        assertThat(FreeReportServiceImpl.excelRawText(12345678d)).isEqualTo("12345678");
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

        // 完整原值必须与 Excel 编辑栏所见**一模一样**：
        //   会计格式的 0   -> 编辑栏 "0"（不是 "0.0"）
        //   极小值         -> 编辑栏 "-5.00000000069889E-07"（Excel 本就用科学计数法，指数两位）
        Map<String, String> r3 = om.readValue(rows.get(2).getDataJson(), new TypeReference<>() {});
        assertThat(r3.get("col_9")).isEqualTo("-");
        assertThat(r3.get("col_9__raw")).isEqualTo("0");

        Map<String, String> r4 = om.readValue(rows.get(3).getDataJson(), new TypeReference<>() {});
        assertThat(r4.get("col_9")).isEqualTo("-0.00");
        assertThat(r4.get("col_9__raw")).isEqualTo("-5.00000000069889E-07");

        // 普通数值：编辑栏是完整精度的十进制，不能被 General 渲染截断成 0.4988888977
        Map<String, String> r1 = om.readValue(rows.get(0).getDataJson(), new TypeReference<>() {});
        assertThat(r1.get("col_4__raw")).isEqualTo("4540.69998");
        // 百分比列：编辑栏带 % 且已乘 100（详见 importExcel_percentFormat_* 用例）
        Map<String, String> r2 = om.readValue(rows.get(1).getDataJson(), new TypeReference<>() {});
        assertThat(r2.get("col_8__raw")).isEqualTo("49.8888897666%");
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
