package com.bank.branch.platform.yundun.controller;

import com.bank.branch.platform.yundun.dto.AccountabilityViolationQuery;
import com.bank.branch.platform.yundun.dto.AccountabilityViolationSaveReq;
import com.bank.branch.platform.yundun.dto.CreditViolationQuery;
import com.bank.branch.platform.yundun.dto.CreditViolationSaveReq;
import com.bank.branch.platform.yundun.dto.ViolationImportReq;
import com.bank.branch.platform.yundun.service.AccountabilityViolationService;
import com.bank.branch.platform.yundun.service.CreditViolationService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 人员违规和信贷风险导入对文本日期的兼容性契约。 */
class ViolationTextDateImportTest {

    @Test
    void accountabilityImportShouldAcceptTextPenaltyDates() throws Exception {
        AccountabilityViolationService service = mock(AccountabilityViolationService.class);
        when(service.importRows(any())).thenReturn(1);
        AccountabilityViolationController controller = new AccountabilityViolationController(service);

        controller.importExcel(importRequest("accountability.xlsx", accountabilityTextDateWorkbook()));

        var rows = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(service).importRows(rows.capture());
        @SuppressWarnings("unchecked")
        List<AccountabilityViolationSaveReq> imported = (List<AccountabilityViolationSaveReq>) rows.getValue();
        assertThat(imported).singleElement().satisfies(row -> {
            assertThat(row.getPenaltyTime()).isEqualTo(LocalDateTime.of(2026, 8, 18, 0, 0));
            assertThat(row.getPenaltyReleaseTime()).isEqualTo(LocalDateTime.of(2026, 9, 19, 12, 13, 14));
        });
    }

    @Test
    void creditImportShouldAcceptTextDateFields() throws Exception {
        CreditViolationService service = mock(CreditViolationService.class);
        when(service.importRows(any())).thenReturn(1);
        CreditViolationController controller = new CreditViolationController(service);

        controller.importExcel(importRequest("credit.xlsx", creditTextDateWorkbook()));

        var rows = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(service).importRows(rows.capture());
        @SuppressWarnings("unchecked")
        List<CreditViolationSaveReq> imported = (List<CreditViolationSaveReq>) rows.getValue();
        assertThat(imported).singleElement().satisfies(row -> {
            assertThat(row.getResponsibilityDeterminationTime()).isEqualTo(LocalDateTime.of(2026, 8, 18, 0, 0));
            assertThat(row.getSubmissionTime()).isEqualTo(LocalDateTime.of(2026, 9, 19, 12, 13, 14));
        });
    }

    private static ViolationImportReq importRequest(String filename, byte[] workbook) {
        ViolationImportReq request = new ViolationImportReq();
        request.setFile(new MockMultipartFile("file", filename,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook));
        request.setReason("文本日期导入测试");
        return request;
    }

    private static byte[] accountabilityTextDateWorkbook() throws Exception {
        AccountabilityViolationService exportService = mock(AccountabilityViolationService.class);
        AccountabilityViolationSaveReq source = new AccountabilityViolationSaveReq();
        source.setWorkNumber("ACCOUNTABILITY-1");
        when(exportService.exportRows(any(AccountabilityViolationQuery.class), isNull())).thenReturn(List.of(source));
        AccountabilityViolationController exportController = new AccountabilityViolationController(exportService);
        MockHttpServletResponse response = new MockHttpServletResponse();
        exportController.export(new AccountabilityViolationQuery(), null, response);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            Row data = sheet.getRow(1);
            data.getCell(findColumn(header, "处罚时间(*)")).setCellValue("2026-08-18");
            data.getCell(findColumn(header, "处罚解除时间(*)")).setCellValue("2026.09.19 12:13:14");
            return writeWorkbook(workbook);
        }
    }

    private static byte[] creditTextDateWorkbook() throws Exception {
        CreditViolationService exportService = mock(CreditViolationService.class);
        CreditViolationSaveReq source = new CreditViolationSaveReq();
        source.setEmployeeNumber("CREDIT-1");
        when(exportService.exportRows(any(CreditViolationQuery.class), isNull())).thenReturn(List.of(source));
        CreditViolationController exportController = new CreditViolationController(exportService);
        MockHttpServletResponse response = new MockHttpServletResponse();
        exportController.export(new CreditViolationQuery(), null, response);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Row data = workbook.getSheetAt(0).getRow(4);
            data.getCell(36).setCellValue("2026/08/18");
            data.getCell(38).setCellValue("2026.09.19 12:13:14");
            return writeWorkbook(workbook);
        }
    }

    private static int findColumn(Row header, String value) {
        for (Cell cell : header) {
            if (value.equals(cell.getStringCellValue())) {
                return cell.getColumnIndex();
            }
        }
        throw new AssertionError("未找到表头：" + value);
    }

    private static byte[] writeWorkbook(XSSFWorkbook workbook) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        return output.toByteArray();
    }
}
