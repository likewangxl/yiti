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
