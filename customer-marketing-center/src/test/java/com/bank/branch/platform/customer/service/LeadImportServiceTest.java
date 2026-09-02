package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.resp.LeadImportPreviewResp;
import com.bank.branch.platform.customer.entity.LeadImportBatch;
import com.bank.branch.platform.customer.enums.BatchStatus;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.customer.mapper.LeadImportBatchMapper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LeadImportService 单元测试（TDD）
 * 测试导入预览、执行导入、查询批次列表等功能。
 */
@ExtendWith(MockitoExtension.class)
class LeadImportServiceTest {

    @Mock
    private LeadImportBatchMapper batchMapper;

    @Mock
    private CustLeadMapper leadMapper;

    @InjectMocks
    private LeadImportService leadImportService;

    // ==================== preview ====================

    @Test
    void preview_shouldReturnRowCountAndCreateBatch() {
        // given: 一个包含 CSV 头行 + 2 条数据行的文件
        String csvContent = "客户名称,统一社会信用代码,联系人,手机号\n" +
                "企业A,91110000123456789A,张三,13800000001\n" +
                "企业B,91110000123456789B,李四,13900000002\n";
        MultipartFile file = new MockMultipartFile(
                "file", "leads.csv", "text/csv", csvContent.getBytes()
        );

        when(batchMapper.insert(any(LeadImportBatch.class))).thenReturn(1);

        // when
        LeadImportPreviewResp result = leadImportService.preview(file, "E001", "ORG001");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getBatchId()).isNotNull();
        verify(batchMapper).insert(any(LeadImportBatch.class));
    }

    @Test
    void preview_shouldThrowWhenFileIsEmpty() {
        // given: 空文件
        MultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.csv", "text/csv", new byte[0]
        );

        // when/then
        assertThatThrownBy(() -> leadImportService.preview(emptyFile, "E001", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.IMPORT_FILE_EMPTY.getCode());

        verify(batchMapper, never()).insert(any(LeadImportBatch.class));
    }

    @Test
    void preview_shouldThrowWhenFileFormatNotAllowed() {
        // given: 非 csv/xlsx/xls 扩展名（CUST-42203）
        MultipartFile pdfFile = new MockMultipartFile(
                "file", "leads.pdf", "application/pdf", "fake".getBytes()
        );

        assertThatThrownBy(() -> leadImportService.preview(pdfFile, "E001", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.IMPORT_FILE_FORMAT_INVALID.getCode());

        verify(batchMapper, never()).insert(any(LeadImportBatch.class));
    }

    @Test
    void preview_shouldThrowWhenFilenameMissingExtension() {
        // given: 缺少扩展名（CUST-42203）
        MultipartFile noExtFile = new MockMultipartFile(
                "file", "leads_noext", "text/plain", "data".getBytes()
        );

        assertThatThrownBy(() -> leadImportService.preview(noExtFile, "E001", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.IMPORT_FILE_FORMAT_INVALID.getCode());
    }

    @Test
    void preview_shouldThrowWhenFileTooLarge() {
        // given: 文件 > 10MB（CUST-42204）。用 12MB 字节数组确保超阈值
        byte[] big = new byte[12 * 1024 * 1024];
        // 头一行写有效内容，避免空文件分支抢先抛 40005
        byte[] header = "客户名称,统一社会信用代码,联系人,手机号\n".getBytes();
        System.arraycopy(header, 0, big, 0, header.length);
        MultipartFile bigFile = new MockMultipartFile(
                "file", "leads.csv", "text/csv", big
        );

        assertThatThrownBy(() -> leadImportService.preview(bigFile, "E001", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.IMPORT_FILE_TOO_LARGE.getCode());

        verify(batchMapper, never()).insert(any(LeadImportBatch.class));
    }

    @Test
    void preview_shouldThrowWhenRowsExceedLimit() {
        // given: 数据行数 > 5000（CUST-42205）。生成头 + 5001 行数据
        StringBuilder sb = new StringBuilder("客户名称,统一社会信用代码,联系人,手机号\n");
        for (int i = 0; i < 5001; i++) {
            sb.append("企业").append(i).append(",cred,zhang,13800000000\n");
        }
        MultipartFile manyRows = new MockMultipartFile(
                "file", "leads.csv", "text/csv", sb.toString().getBytes()
        );

        assertThatThrownBy(() -> leadImportService.preview(manyRows, "E001", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.IMPORT_ROWS_TOO_MANY.getCode());

        verify(batchMapper, never()).insert(any(LeadImportBatch.class));
    }

    // ==================== execute ====================

    @Test
    void execute_shouldInsertLeadsAndUpdateBatchStatus() {
        // given: 批次状态为 CREATED
        LeadImportBatch batch = buildBatch("batch-001", BatchStatus.CREATED.getCode());
        when(batchMapper.selectById("batch-001")).thenReturn(batch);
        when(batchMapper.updateById(any(LeadImportBatch.class))).thenReturn(1);

        // when: execute 只更新批次状态为 PENDING_APPROVAL（简化实现不实际插入线索）
        leadImportService.execute("batch-001", "E001", "ORG001");

        // then
        verify(batchMapper).updateById(any(LeadImportBatch.class));
    }

    @Test
    void execute_shouldThrowWhenBatchNotFound() {
        // given: 批次不存在
        when(batchMapper.selectById("not-exist")).thenReturn(null);

        // when/then
        assertThatThrownBy(() -> leadImportService.execute("not-exist", "E001", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.BATCH_NOT_FOUND.getCode());
    }

    // ==================== getBatchById ====================

    @Test
    void getBatchById_shouldReturnBatch() {
        // given
        LeadImportBatch batch = buildBatch("batch-001", BatchStatus.CREATED.getCode());
        when(batchMapper.selectById("batch-001")).thenReturn(batch);

        // when
        LeadImportBatch result = leadImportService.getBatchById("batch-001");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("batch-001");
    }

    @Test
    void getBatchById_shouldThrowWhenNotFound() {
        // given
        when(batchMapper.selectById("not-exist")).thenReturn(null);

        // when/then
        assertThatThrownBy(() -> leadImportService.getBatchById("not-exist"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.BATCH_NOT_FOUND.getCode());
    }

    // ==================== listBatches ====================

    @Test
    void listBatches_shouldCalculateOffset() {
        // given: pageNo=2, pageSize=10 -> offset=10
        LeadImportBatch batch = buildBatch("batch-001", BatchStatus.CREATED.getCode());
        when(batchMapper.selectPage(isNull(), isNull(), eq(10), eq(10)))
                .thenReturn(Collections.singletonList(batch));
        when(batchMapper.countPage(isNull(), isNull())).thenReturn(15L);

        // when
        PageResult<LeadImportBatch> result = leadImportService.listBatches(null, null, 2, 10);

        // then
        assertThat(result.getPageNo()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getTotal()).isEqualTo(15L);
        assertThat(result.getRecords()).hasSize(1);
        verify(batchMapper).selectPage(null, null, 10, 10);
    }

    // ==================== preview - successCount/failCount/errorSamples ====================

    /**
     * Task 8.2 TDD: 当前实现无行级校验，preview 应默认 successCount=totalCount, failCount=0, errorSamples 空列表。
     */
    @Test
    void preview_validTouchRestrictionValuesAreSuccessful() {
        // given: 一个包含 CSV 头行 + 3 条数据行的文件
        String csvContent = "客户名称,统一社会信用代码,联系人,手机号,是否触达限制\n" +
                "企业A,91110000123456789A,张三,13800000001,是\n" +
                "企业B,91110000123456789B,李四,13900000002,否\n" +
                "企业C,91110000123456789C,王五,13700000003,是\n";
        MultipartFile file = new MockMultipartFile(
                "file", "leads.csv", "text/csv", csvContent.getBytes()
        );

        when(batchMapper.insert(any(LeadImportBatch.class))).thenReturn(1);

        // when
        LeadImportPreviewResp resp = leadImportService.preview(file, "E1", "ORG1");

        // then: 触达限制列通过校验时全部成功
        assertThat(resp.getSuccessCount())
                .as("合法触达限制值 successCount 应等于 totalRows")
                .isEqualTo(resp.getTotalRows());
        assertThat(resp.getFailCount())
                .as("合法触达限制值 failCount 应为 0")
                .isEqualTo(0);
        assertThat(resp.getErrorSamples())
                .as("合法触达限制值 errorSamples 应为空列表")
                .isEmpty();
    }

    @Test
    void preview_shouldRejectMissingTouchRestrictionColumnAsRowErrors() {
        String csvContent = "客户名称,统一社会信用代码,联系人,手机号\n" +
                "企业A,91110000123456789A,张三,13800000001\n";
        MultipartFile file = new MockMultipartFile(
                "file", "leads.csv", "text/csv", csvContent.getBytes()
        );
        when(batchMapper.insert(any(LeadImportBatch.class))).thenReturn(1);

        LeadImportPreviewResp resp = leadImportService.preview(file, "E1", "ORG1");

        assertThat(resp.getTotalRows()).isEqualTo(1);
        assertThat(resp.getErrorRows()).isEqualTo(1);
        assertThat(resp.getSuccessCount()).isZero();
        assertThat(resp.getFailCount()).isEqualTo(1);
        assertThat(resp.getErrorSamples()).singleElement()
                .extracting("field", "message")
                .containsExactly("是否触达限制", "是否触达限制列不能为空");
        ArgumentCaptor<LeadImportBatch> batchCaptor = ArgumentCaptor.forClass(LeadImportBatch.class);
        verify(batchMapper).insert(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getStatus()).isEqualTo(BatchStatus.VALIDATION_FAILED.getCode());
    }

    @Test
    void preview_shouldRejectInvalidTouchRestrictionValues() {
        String csvContent = "客户名称,统一社会信用代码,是否触达限制\n" +
                "企业A,91110000123456789A,是\n" +
                "企业B,91110000123456789B,maybe\n";
        MultipartFile file = new MockMultipartFile(
                "file", "leads.csv", "text/csv", csvContent.getBytes()
        );
        when(batchMapper.insert(any(LeadImportBatch.class))).thenReturn(1);

        LeadImportPreviewResp resp = leadImportService.preview(file, "E1", "ORG1");

        assertThat(resp.getTotalRows()).isEqualTo(2);
        assertThat(resp.getErrorRows()).isEqualTo(1);
        assertThat(resp.getSuccessCount()).isEqualTo(1);
        assertThat(resp.getFailCount()).isEqualTo(1);
        assertThat(resp.getErrorSamples()).singleElement()
                .extracting("rowIndex", "field")
                .containsExactly(2, "是否触达限制");
    }

    @Test
    void preview_xlsx_shouldValidateTouchRestrictionColumn() throws Exception {
        when(batchMapper.insert(any(LeadImportBatch.class))).thenReturn(1);
        MultipartFile file = new MockMultipartFile(
                "file", "leads.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbookBytes(new XSSFWorkbook(), "是"));

        LeadImportPreviewResp resp = leadImportService.preview(file, "E1", "ORG1");

        assertThat(resp.getTotalRows()).isEqualTo(1);
        assertThat(resp.getSuccessCount()).isEqualTo(1);
        assertThat(resp.getFailCount()).isZero();
    }

    @Test
    void preview_xls_shouldValidateTouchRestrictionColumn() throws Exception {
        when(batchMapper.insert(any(LeadImportBatch.class))).thenReturn(1);
        MultipartFile file = new MockMultipartFile(
                "file", "leads.xls", "application/vnd.ms-excel", workbookBytes(new HSSFWorkbook(), "否"));

        LeadImportPreviewResp resp = leadImportService.preview(file, "E1", "ORG1");

        assertThat(resp.getTotalRows()).isEqualTo(1);
        assertThat(resp.getSuccessCount()).isEqualTo(1);
        assertThat(resp.getFailCount()).isZero();
    }

    @Test
    void execute_shouldRejectBatchWithValidationErrors() {
        LeadImportBatch batch = buildBatch("batch-invalid", BatchStatus.VALIDATION_FAILED.getCode());
        batch.setErrorRowCount(1);
        when(batchMapper.selectById("batch-invalid")).thenReturn(batch);

        assertThatThrownBy(() -> leadImportService.execute("batch-invalid", "E001", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.LEAD_IMPORT_VALIDATION_FAILED.getCode());
        verify(batchMapper, never()).updateById(any(LeadImportBatch.class));
    }

    @Test
    void preview_shouldMarkParseFailureAsValidationFailed() {
        MultipartFile file = new MockMultipartFile(
                "file", "leads.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "not-an-excel-file".getBytes()
        );
        when(batchMapper.insert(any(LeadImportBatch.class))).thenReturn(1);

        LeadImportPreviewResp resp = leadImportService.preview(file, "E1", "ORG1");

        assertThat(resp.getTotalRows()).isZero();
        assertThat(resp.getErrorSummary()).isEqualTo("文件解析失败，请检查文件内容");
        ArgumentCaptor<LeadImportBatch> batchCaptor = ArgumentCaptor.forClass(LeadImportBatch.class);
        verify(batchMapper).insert(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getStatus()).isEqualTo(BatchStatus.VALIDATION_FAILED.getCode());
    }

    private byte[] workbookBytes(Workbook workbook, String touchRestricted) throws Exception {
        try (Workbook toWrite = workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = toWrite.createSheet();
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("客户名称");
            header.createCell(1).setCellValue("是否触达限制");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("企业A");
            row.createCell(1).setCellValue(touchRestricted);
            toWrite.write(output);
            return output.toByteArray();
        }
    }

    // ============================= 辅助方法 =============================

    private LeadImportBatch buildBatch(String id, String status) {
        LeadImportBatch batch = new LeadImportBatch();
        batch.setId(id);
        batch.setBatchNo("BATCH_20260414_0001");
        batch.setStatus(status);
        batch.setTotalRowCount(2);
        batch.setErrorRowCount(0);
        batch.setCreatedBy("E001");
        batch.setOwnerOrgId("ORG001");
        return batch;
    }
}
