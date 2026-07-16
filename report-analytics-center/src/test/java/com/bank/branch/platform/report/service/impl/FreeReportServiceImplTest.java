package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.storage.FileCategory;
import com.bank.branch.platform.report.entity.RptFreeReportBatch;
import com.bank.branch.platform.report.entity.RptFreeReportRow;
import com.bank.branch.platform.report.mapper.FreeReportBatchMapper;
import com.bank.branch.platform.report.mapper.FreeReportRowMapper;
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

    /** 下载导出：数字格显示截断两位(不四舍五入)，完整原值放"数据有效性输入提示"(点击/选中弹出)；工号/姓名原样文本。 */
    @Test
    void exportFilteredExcel_truncatesDecimal_keepsFullValueInPrompt() throws Exception {
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
            // 金额列 → 数值截断 3.17(不四舍五入,3.1779998→3.17) + 数字格式 0.00
            Cell amt = data.getCell(2);
            assertThat(amt.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(amt.getNumericCellValue()).isCloseTo(3.17, within(1e-9));
            assertThat(amt.getCellStyle().getDataFormatString()).isEqualTo("0.00");
            // 完整原值在"数据有效性输入提示"里(点击/选中弹出)
            boolean promptHasFull = sheet.getDataValidations().stream()
                    .anyMatch(dv -> "3.1779998".equals(dv.getPromptBoxText()));
            assertThat(promptHasFull).isTrue();
        }
    }

    @Test
    void truncate2_rule() {
        assertThat(FreeReportServiceImpl.truncate2("3.1779998")).isEqualTo("3.17");
        assertThat(FreeReportServiceImpl.truncate2("3.1")).isEqualTo("3.10");
        assertThat(FreeReportServiceImpl.truncate2("-2.999")).isEqualTo("-2.99");
        assertThat(FreeReportServiceImpl.truncate2("1001")).isEqualTo("1001");
        assertThat(FreeReportServiceImpl.truncate2("张三")).isEqualTo("张三");
    }
}
