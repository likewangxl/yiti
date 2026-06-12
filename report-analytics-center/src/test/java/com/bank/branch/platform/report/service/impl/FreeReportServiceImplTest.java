package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.storage.FileCategory;
import com.bank.branch.platform.report.entity.RptFreeReportBatch;
import com.bank.branch.platform.report.mapper.FreeReportBatchMapper;
import com.bank.branch.platform.report.mapper.FreeReportRowMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
}
