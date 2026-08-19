package com.bank.branch.platform.yundun.controller;

import com.bank.branch.platform.yundun.dto.CreditViolationQuery;
import com.bank.branch.platform.yundun.dto.CreditViolationSaveReq;
import com.bank.branch.platform.yundun.dto.ViolationImportReq;
import com.bank.branch.platform.yundun.service.CreditViolationService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 信贷风险数据行按客户字段合并及导入还原契约。 */
class CreditViolationCustomerMergeTest {

    @Test
    void exportShouldMergeAThroughHAndAEForEachContiguousMatchingCustomer() throws Exception {
        CreditViolationService service = mock(CreditViolationService.class);
        when(service.exportRows(any(CreditViolationQuery.class), isNull())).thenReturn(List.of(
                row("C-1", "客户一", "IOU-1", "有", "是", "是", "DOC-1", "E-1"),
                row("C-1", "客户一", "IOU-1", "有", "是", "是", "DOC-1", "E-2"),
                row("C-1", "客户一", "IOU-1", "有", "是", "否", "DOC-1", "E-3"),
                row("C-1", "客户一", "IOU-1", "有", "是", "是", "DOC-2", "E-4")));
        CreditViolationController controller = new CreditViolationController(service);

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.export(new CreditViolationQuery(), null, response);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int column : customerColumns()) {
                assertThat(sheet.getMergedRegions()).contains(new CellRangeAddress(4, 5, column, column));
            }
            assertThat(sheet.getMergedRegions())
                    .doesNotContain(new CellRangeAddress(4, 5, 0, 7))
                    .doesNotContain(new CellRangeAddress(6, 7, 0, 0))
                    .doesNotContain(new CellRangeAddress(6, 7, 30, 30));
        }
    }

    @Test
    void importShouldRestoreMergedCustomerValuesIntoEveryDataRow() throws Exception {
        CreditViolationService service = mock(CreditViolationService.class);
        when(service.importRows(any())).thenReturn(2);
        CreditViolationController controller = new CreditViolationController(service);

        byte[] mergedWorkbook = mergedRowsWorkbook();
        ViolationImportReq request = new ViolationImportReq();
        request.setFile(new MockMultipartFile("file", "credit.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", mergedWorkbook));
        request.setReason("合并单元格导入测试");

        controller.importExcel(request);

        var rows = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(service).importRows(rows.capture());
        @SuppressWarnings("unchecked")
        List<CreditViolationSaveReq> imported = (List<CreditViolationSaveReq>) rows.getValue();
        assertThat(imported).hasSize(2);
        assertThat(imported).extracting(CreditViolationSaveReq::getAccountabilityCode)
                .containsExactly("C-1", "C-1");
        assertThat(imported).extracting(CreditViolationSaveReq::getClientName)
                .containsExactly("客户一", "客户一");
        assertThat(imported).extracting(CreditViolationSaveReq::getIsMicrofinanceCreditBusiness)
                .containsExactly("是", "是");
        assertThat(imported).extracting(CreditViolationSaveReq::getNameAndNumberOfAccountabilityDocument)
                .containsExactly("DOC-1", "DOC-1");
    }

    private static byte[] mergedRowsWorkbook() throws Exception {
        CreditViolationService service = mock(CreditViolationService.class);
        when(service.exportRows(any(CreditViolationQuery.class), isNull())).thenReturn(List.of(
                row("C-1", "客户一", "IOU-1", "有", "是", "是", "DOC-1", "E-1"),
                row("C-1", "客户一", "IOU-1", "有", "是", "是", "DOC-1", "E-2")));
        CreditViolationController controller = new CreditViolationController(service);
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.export(new CreditViolationQuery(), null, response);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row child = sheet.getRow(5);
            for (int column : customerColumns()) {
                child.getCell(column).setBlank();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static int[] customerColumns() {
        return new int[]{0, 1, 2, 3, 4, 5, 6, 7, 30};
    }

    private static CreditViolationSaveReq row(String code, String client, String iou,
                                              String responsibility, String headOffice,
                                              String microfinance, String document, String employee) {
        CreditViolationSaveReq row = new CreditViolationSaveReq();
        row.setAccountabilityCode(code);
        row.setClientName(client);
        row.setIouNumber(iou);
        row.setInsurancePrincipal(new BigDecimal("100"));
        row.setEstimatedLoss(new BigDecimal("20"));
        row.setIsResponsibility(responsibility);
        row.setIsAuditByHeadOffice(headOffice);
        row.setIsMicrofinanceCreditBusiness(microfinance);
        row.setNameAndNumberOfAccountabilityDocument(document);
        row.setEmployeeNumber(employee);
        return row;
    }
}
