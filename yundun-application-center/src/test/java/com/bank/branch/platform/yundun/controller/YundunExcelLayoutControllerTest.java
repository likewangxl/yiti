package com.bank.branch.platform.yundun.controller;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.yundun.dto.AccountabilityViolationQuery;
import com.bank.branch.platform.yundun.dto.AccountabilityViolationSaveReq;
import com.bank.branch.platform.yundun.dto.CreditViolationQuery;
import com.bank.branch.platform.yundun.dto.CreditViolationSaveReq;
import com.bank.branch.platform.yundun.service.AccountabilityViolationService;
import com.bank.branch.platform.yundun.service.CreditViolationService;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 两类云盾 Excel 模板及导出文件的可读布局契约。 */
class YundunExcelLayoutControllerTest {

    @Test
    void accountabilityTemplateAndExportShouldUseReadableHeaderHeightAndWidths() throws Exception {
        AccountabilityViolationService service = mock(AccountabilityViolationService.class);
        when(service.exportRows(any(AccountabilityViolationQuery.class), isNull()))
                .thenReturn(List.of(new AccountabilityViolationSaveReq()));
        AccountabilityViolationController controller = new AccountabilityViolationController(service);

        MockHttpServletResponse template = new MockHttpServletResponse();
        controller.downloadImportTemplate(template);
        assertAccountabilityLayout(template, "导入模板");

        MockHttpServletResponse export = new MockHttpServletResponse();
        controller.export(new AccountabilityViolationQuery(), null, export);
        assertAccountabilityLayout(export, "导出");
    }

    @Test
    void creditTemplateAndExportShouldExactlyFollowReferenceFourRowHeaderLayout() throws Exception {
        CreditViolationService service = mock(CreditViolationService.class);
        CreditViolationSaveReq firstCustomerRow = creditRow("C001", "客户甲", "借据001", "100", "10", "有", "否", "是", "文件001");
        CreditViolationSaveReq sameCustomerRow = creditRow("C001", "客户甲", "借据001", "100", "10", "有", "否", "是", "文件001");
        CreditViolationSaveReq differentCustomerRow = creditRow("C001", "客户甲", "借据001", "100", "10", "有", "否", "否", "文件001");
        when(service.exportRows(any(CreditViolationQuery.class), isNull()))
                .thenReturn(List.of(firstCustomerRow, sameCustomerRow, differentCustomerRow));
        CreditViolationController controller = new CreditViolationController(service);

        MockHttpServletResponse template = new MockHttpServletResponse();
        controller.downloadImportTemplate(template);
        assertCreditLayout(template, "导入模板", false);
        assertNoCustomerDataMerge(template);

        MockHttpServletResponse export = new MockHttpServletResponse();
        controller.export(new CreditViolationQuery(), null, export);
        assertCreditLayout(export, "导出", true);
        assertCustomerDataMerge(export);
    }

    private static void assertAccountabilityLayout(MockHttpServletResponse response, String scenario)
            throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header).as("人员违规%s应生成表头行", scenario).isNotNull();
            assertThat(header.getHeightInPoints())
                    .as("人员违规%s表头应提高行高", scenario)
                    .isGreaterThan(30F);

            int withholdingInstructionsColumn = findColumn(header, "扣发说明（落实情况）");
            assertThat(withholdingInstructionsColumn).isGreaterThanOrEqualTo(0);
            assertThat(sheet.getColumnWidth(withholdingInstructionsColumn) / 256)
                    .as("人员违规%s长中文标题列应有足够宽度", scenario)
                    .isGreaterThanOrEqualTo(20);
        }
    }

    private static void assertCreditLayout(MockHttpServletResponse response, String scenario,
                                           boolean includeLegacyColumns)
            throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row first = sheet.getRow(0);
            Row groups = sheet.getRow(1);
            Row subgroups = sheet.getRow(2);
            Row fields = sheet.getRow(3);
            assertThat(first).as("信贷风险%s应生成第1行空白表头", scenario).isNotNull();
            assertThat(groups).as("信贷风险%s应生成第2行分组表头", scenario).isNotNull();
            assertThat(subgroups).as("信贷风险%s应生成第3行子分组表头", scenario).isNotNull();
            assertThat(fields).as("信贷风险%s应生成第4行字段表头", scenario).isNotNull();
            assertThat(first.getHeightInPoints()).isEqualTo(18F);
            assertThat(groups.getHeightInPoints()).isEqualTo(18.75F);
            assertThat(subgroups.getHeightInPoints()).isEqualTo(21F);
            assertThat(fields.getHeightInPoints()).isEqualTo(39F);
            assertThat(sheet.getColumnWidth(0) / 256D).isEqualTo(12.75D);
            assertThat(sheet.getColumnWidth(22) / 256D).isEqualTo(25D);
            assertThat(sheet.getColumnWidth(23) / 256D).isEqualTo(26.75D);
            assertThat(sheet.getColumnWidth(29) / 256D).isEqualTo(26D);
            assertMergedGroup(sheet, 12, 14, "责任认定对象所在机构", scenario);
            assertMergedGroup(sheet, 15, 20, "责任认定", scenario);
            assertMergedGroup(sheet, 21, 24, "经济扣发", scenario);
            assertMergedGroup(sheet, 25, 30, "违规问责", scenario);
            assertThat(sheet.getMergedRegions())
                    .contains(new CellRangeAddress(2, 2, 27, 29))
                    .contains(new CellRangeAddress(1, 3, 0, 0))
                    .contains(new CellRangeAddress(0, 0, 1, includeLegacyColumns ? 39 : 30));
            assertIndependentVerticalHeaders(sheet, includeLegacyColumns, scenario);

            assertThat(groups.getCell(5).getStringCellValue()).isEqualTo("认定结果有无责任(有/无)");
            assertThat(subgroups.getCell(12).getStringCellValue()).isEqualTo("机构名称");
            assertThat(fields.getCell(27).getStringCellValue()).isEqualTo("处理类型");
            Cell groupCell = groups.getCell(12);
            assertThat(groupCell.getCellStyle().getFillForegroundColor())
                    .isEqualTo(IndexedColors.GREY_25_PERCENT.getIndex());
            assertThat(groupCell.getCellStyle().getAlignment()).isEqualTo(HorizontalAlignment.CENTER);
            assertThat(groupCell.getCellStyle().getWrapText()).isTrue();
            assertThat(groupCell.getCellStyle().getBorderTop()).isEqualTo(BorderStyle.THIN);
            assertThat(workbook.getFontAt(groupCell.getCellStyle().getFontIndex()).getFontName()).isEqualTo("宋体");
            assertThat(workbook.getFontAt(groupCell.getCellStyle().getFontIndex()).getFontHeightInPoints())
                    .isEqualTo((short) 14);
            assertThat(workbook.getFontAt(groupCell.getCellStyle().getFontIndex()).getBold()).isTrue();
        }
    }

    private static int findColumn(Row row, String text) {
        for (Cell cell : row) {
            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                    && cell.getStringCellValue().contains(text)) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }

    private static void assertMergedGroup(Sheet sheet, int firstColumn, int lastColumn,
                                          String groupName, String scenario) {
        assertThat(sheet.getMergedRegions())
                .as("信贷风险%s第二层应将%s合并为一个分组", scenario, groupName)
                .anySatisfy(region -> assertThat(region)
                        .isEqualTo(new CellRangeAddress(1, 1, firstColumn, lastColumn)));
    }

    private static void assertIndependentVerticalHeaders(Sheet sheet, boolean includeLegacyColumns,
                                                         String scenario) {
        int lastCurrentColumn = includeLegacyColumns ? 26 : 26;
        for (int column = 12; column <= lastCurrentColumn; column++) {
            assertThat(sheet.getMergedRegions())
                    .as("信贷风险%s第三、四层第%s列应独立纵向合并", scenario, column + 1)
                    .contains(new CellRangeAddress(2, 3, column, column));
        }
        assertThat(sheet.getMergedRegions())
                .as("信贷风险%s第三、四层M至O列不可合并成一个区域", scenario)
                .doesNotContain(new CellRangeAddress(2, 3, 12, 14))
                .doesNotContain(new CellRangeAddress(3, 3, 12, 26));
    }

    private static CreditViolationSaveReq creditRow(String accountabilityCode, String clientName, String iouNumber,
                                                     String insurancePrincipal, String estimatedLoss,
                                                     String responsibility, String headOfficeAudit,
                                                     String microfinance, String document) {
        CreditViolationSaveReq row = new CreditViolationSaveReq();
        row.setAccountabilityCode(accountabilityCode);
        row.setClientName(clientName);
        row.setIouNumber(iouNumber);
        row.setInsurancePrincipal(new java.math.BigDecimal(insurancePrincipal));
        row.setEstimatedLoss(new java.math.BigDecimal(estimatedLoss));
        row.setIsResponsibility(responsibility);
        row.setIsAuditByHeadOffice(headOfficeAudit);
        row.setIsMicrofinanceCreditBusiness(microfinance);
        row.setNameAndNumberOfAccountabilityDocument(document);
        return row;
    }

    private static void assertNoCustomerDataMerge(MockHttpServletResponse response) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertThat(workbook.getSheetAt(0).getMergedRegions())
                    .as("空白导入模板不应产生客户数据合并区")
                    .noneMatch(region -> region.getFirstRow() >= 4);
        }
    }

    private static void assertCustomerDataMerge(MockHttpServletResponse response) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int column = 0; column <= 7; column++) {
                assertThat(sheet.getMergedRegions())
                        .as("同一客户第%s列应在第5、6行纵向合并", column + 1)
                        .contains(new CellRangeAddress(4, 5, column, column));
            }
            assertThat(sheet.getMergedRegions())
                    .as("同一客户AE列应在第5、6行纵向合并")
                    .contains(new CellRangeAddress(4, 5, 30, 30))
                    .as("H列不同的第6、7行不能合并")
                    .doesNotContain(new CellRangeAddress(5, 6, 7, 7));
        }
    }
}
