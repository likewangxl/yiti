package com.bank.branch.platform.customer.marketing.lead;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadCreateRequest;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadSubmitResponse;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadImportBatch;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadImportDetail;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadInfo;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadImportBatchMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadImportDetailMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadInfoMapper;
import com.bank.branch.platform.customer.service.marketing.MarketingLeadEntryService;
import com.bank.branch.platform.customer.service.marketing.MarketingLeadImportLocalStorage;
import com.bank.branch.platform.customer.service.marketing.MarketingLeadImportService;
import com.bank.branch.platform.governance.api.FileApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 页面三批量导入的待确认/明细排序契约。 */
@ExtendWith(MockitoExtension.class)
class MarketingLeadImportServiceTest {

    @Mock
    private MarketingLeadImportBatchMapper batchMapper;
    @Mock
    private MarketingLeadImportDetailMapper detailMapper;
    @Mock
    private MarketingLeadInfoMapper leadMapper;
    @Mock
    private MarketingLeadEntryService leadEntryService;
    @Mock
    private MarketingCustomerInfoMapper customerMapper;
    @Mock
    private MarketingLeadImportLocalStorage localStorage;
    @Mock
    private FileApi fileApi;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    @InjectMocks
    private MarketingLeadImportService service;

    @BeforeEach
    void configureLocalStorage() {
        org.mockito.Mockito.lenient().when(localStorage.saveSource(any()))
                .thenReturn(new MarketingLeadImportLocalStorage.StoredFile(
                        "local:source/20260827/test.csv", "0123456789abcdef0123456789abcdef"));
        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any()))
                .thenReturn(transactionStatus);
    }

    @Test
    void waitingConfirmationProcessesOnlyValidRowsAndSkipsWarningRows() {
        MarketingLeadImportBatch batch = new MarketingLeadImportBatch();
        batch.setId(3L);
        batch.setImportStatus("WAITING_CONFIRM");
        batch.setRecordStatus("ACTIVE");
        batch.setImportEmpId("EMP_1");
        batch.setImportOrgId("ORG_1");
        when(batchMapper.selectForUpdate(3L)).thenReturn(batch);

        MarketingLeadImportDetail valid = detail(31L, "VALID");
        MarketingLeadImportDetail warning = detail(32L, "WARNING");
        MarketingLeadInfo generatedLead = new MarketingLeadInfo();
        generatedLead.setId(301L);
        when(leadEntryService.createDraft(any(LeadCreateRequest.class), eq("EMP_1"), eq("ORG_1")))
                .thenReturn(generatedLead);
        when(detailMapper.selectByBatchAndValidationStatus(3L, "VALID"))
                .thenReturn(List.of(valid));
        when(detailMapper.selectByBatchIdOrderByFailure(3L, 0, Integer.MAX_VALUE))
                .thenReturn(List.of(valid, warning));

        service.confirm(3L, "PROCESS_VALID", "EMP_1", "仅处理正常行");

        assertEquals("COMPLETED", batch.getImportStatus());
        assertEquals("PROCESS_VALID", batch.getConfirmAction());
        verify(detailMapper).updateHandlingIf(31L, "PENDING", "GENERATED", 301L);
        verify(detailMapper).updateHandlingIf(32L, "PENDING", "SKIPPED", null);
    }

    @Test
    void importTemplateFieldsBuildLeadRequestAndPatchGeneratedLead() {
        MarketingLeadImportBatch batch = new MarketingLeadImportBatch();
        batch.setId(9L);
        batch.setImportEmpId("EMP_1");
        batch.setImportOrgId("ORG_1");
        batch.setImportStatus("IMPORTING");
        batch.setRecordStatus("ACTIVE");
        doAnswer(invocation -> {
            MarketingLeadImportBatch inserted = invocation.getArgument(0);
            inserted.setId(9L);
            return 1;
        }).when(batchMapper).insert(any(MarketingLeadImportBatch.class));

        List<MarketingLeadImportDetail> insertedDetails = new ArrayList<>();
        doAnswer(invocation -> {
            MarketingLeadImportDetail detail = invocation.getArgument(0);
            detail.setId(91L);
            insertedDetails.add(detail);
            return 1;
        }).when(detailMapper).insert(any(MarketingLeadImportDetail.class));
        when(detailMapper.selectByBatchAndValidationStatus(9L, "VALID"))
                .thenAnswer(invocation -> insertedDetails);
        when(leadMapper.selectActiveByCreditCode("91320100ABC1234567")).thenReturn(null);
        when(customerMapper.selectOne(any())).thenReturn(null);

        MarketingLeadInfo generatedLead = new MarketingLeadInfo();
        generatedLead.setId(901L);
        generatedLead.setLeadSource("MANUAL");
        generatedLead.setLeadStatus("DRAFT");
        when(leadEntryService.createDraft(any(LeadCreateRequest.class), eq("EMP_1"), eq("ORG_1")))
                .thenAnswer(invocation -> {
                    LeadCreateRequest request = invocation.getArgument(0);
                    assertEquals("EXISTING_MARKETING", request.getLeadType());
                    assertEquals("客户甲", request.getCustName());
                    assertEquals("91320100ABC1234567", request.getUnifiedCreditCode());
                    assertEquals(1, request.getIsAccountOpenedSnapshot());
                    assertEquals("CUST-001", request.getCustNo());
                    assertEquals("I01", request.getIndustry());
                    assertEquals("GROUP", request.getGroupType());
                    assertEquals("集团甲", request.getGroupName());
                    assertEquals("CORPORATE", request.getCustomerType());
                    assertEquals(0, request.getIsKeystone());
                    assertEquals("PRIVATE", request.getEnterpriseType());
                    assertEquals(List.of(101L, 102L, 103L), request.getTagIds());
                    assertEquals("SCOPE", request.getDistributionMode());
                    assertEquals(List.of("EMP_2", "EMP_3", "EMP_4"), request.getManagerEmpIds());
                    assertEquals(0, request.getTouchRestricted());
                    assertEquals("导入说明", request.getCustomerDesc());
                    assertEquals(new BigDecimal("12345600.00"), request.getCreditAmount());
                    assertEquals(new BigDecimal("10000000"), request.getCreditExposureAmount());
                    return generatedLead;
                });
        LeadSubmitResponse submitResponse = new LeadSubmitResponse();
        submitResponse.setLeadId(901L);
        submitResponse.setLeadStatus("IN_APPROVAL");
        when(leadEntryService.submit(901L, "EMP_1", "ORG_1")).thenAnswer(invocation -> {
            assertEquals("LEAD_IMPORT", generatedLead.getLeadSource());
            assertEquals(9L, generatedLead.getImportBatchId());
            return submitResponse;
        });

        // 逗号属于 CSV 分隔符，数值千分位通过引号传递。
        String csv = "线索类型,客户名称,统一社会信用代码,是否开户,客户号,所属行业,所属集团类型,所属集团名称,客户类型,是否基石客户,企业类型,客户标签,分配方式,指定客户经理范围,是否触达限制,客户说明,授信金额（万元）,授信敞口金额（万元）\n"
                + "存量客户营销线索,客户甲,91320100ABC1234567,是,CUST-001,I01,GROUP,集团甲,CORPORATE,否,PRIVATE,101；102、103,指定客户经理范围,EMP_2、EMP_3;EMP_4,否,导入说明,\"1,234.56\",\"1,000\"\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "lead-import.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        var response = service.create(file, "EMP_1", "ORG_1");

        assertEquals("COMPLETED", response.getBatch().getImportStatus());
        assertEquals("IN_APPROVAL", response.getBatch().getApprovalSummaryStatus());
        assertEquals("{", insertedDetails.get(0).getRawRowJson().substring(0, 1));
        assertEquals(new BigDecimal("12345600.00"), insertedDetails.get(0).getCreditAmount());
        assertEquals(new BigDecimal("10000000"), insertedDetails.get(0).getCreditExposureAmount());
        assertEquals("LEAD_IMPORT", generatedLead.getLeadSource());
        assertEquals(9L, generatedLead.getImportBatchId());
        assertEquals(2, generatedLead.getBatchRowNo());
        verify(leadMapper).updateById(generatedLead);
        verify(leadEntryService).submit(901L, "EMP_1", "ORG_1");
        verify(detailMapper).updateHandlingIf(91L, "PENDING", "GENERATED", 901L);
        verify(localStorage).saveSource(file);
        verifyNoInteractions(fileApi);
    }

    @Test
    void importedLeadSubmissionFailureIsRecordedAsRowError() {
        stubBatchInsert(17L);
        List<MarketingLeadImportDetail> insertedDetails = new ArrayList<>();
        doAnswer(invocation -> {
            MarketingLeadImportDetail detail = invocation.getArgument(0);
            detail.setId(171L);
            insertedDetails.add(detail);
            return 1;
        }).when(detailMapper).insert(any(MarketingLeadImportDetail.class));
        when(detailMapper.selectByBatchAndValidationStatus(17L, "VALID"))
                .thenAnswer(invocation -> insertedDetails.stream()
                        .filter(detail -> "VALID".equals(detail.getValidationStatus())).toList());
        when(customerMapper.selectOne(any())).thenReturn(null);
        when(leadMapper.selectActiveByCreditCode(anyString())).thenReturn(null);

        MarketingLeadInfo generatedLead = new MarketingLeadInfo();
        generatedLead.setId(1701L);
        when(leadEntryService.createDraft(any(LeadCreateRequest.class), eq("EMP_1"), eq("ORG_1")))
                .thenReturn(generatedLead);
        org.mockito.Mockito.doThrow(new BizException(
                        "MARKETING_LEAD_WORKFLOW_START_FAILED", "线索审批流程启动失败"))
                .when(leadEntryService).submit(1701L, "EMP_1", "ORG_1");

        String csv = "客户名称,统一社会信用代码,是否触达限制\n"
                + "导入失败企业,91320100ABC1234567,否\n";

        var response = service.create(csvFile(csv), "EMP_1", "ORG_1");

        assertEquals("ALL_FAILED", response.getBatch().getImportStatus());
        assertEquals("NOT_SUBMITTED", response.getBatch().getApprovalSummaryStatus());
        assertEquals(1, response.getBatch().getErrorCount());
        assertEquals(0, response.getBatch().getGeneratedLeadCount());
        assertEquals("ERROR", insertedDetails.get(0).getValidationStatus());
        assertEquals("MARKETING_LEAD_WORKFLOW_START_FAILED", insertedDetails.get(0).getErrorCode());
        assertEquals("SKIPPED", insertedDetails.get(0).getHandlingStatus());
        verify(leadEntryService).submit(1701L, "EMP_1", "ORG_1");
        verify(transactionManager).rollback(transactionStatus);
        verify(detailMapper).updateById(insertedDetails.get(0));
    }

    @Test
    void invalidTouchRestrictedValueIsSavedAsErrorAndDoesNotStopOtherRows() {
        MarketingLeadImportBatch batch = new MarketingLeadImportBatch();
        batch.setId(10L);
        batch.setImportEmpId("EMP_1");
        batch.setImportOrgId("ORG_1");
        batch.setRecordStatus("ACTIVE");
        doAnswer(invocation -> {
            MarketingLeadImportBatch inserted = invocation.getArgument(0);
            inserted.setId(10L);
            return 1;
        }).when(batchMapper).insert(any(MarketingLeadImportBatch.class));
        List<MarketingLeadImportDetail> insertedDetails = new ArrayList<>();
        doAnswer(invocation -> {
            MarketingLeadImportDetail detail = invocation.getArgument(0);
            detail.setId(100L + insertedDetails.size());
            insertedDetails.add(detail);
            return 1;
        }).when(detailMapper).insert(any(MarketingLeadImportDetail.class));
        when(detailMapper.selectByBatchAndValidationStatus(10L, "VALID"))
                .thenAnswer(invocation -> insertedDetails.stream()
                        .filter(detail -> "VALID".equals(detail.getValidationStatus())).toList());
        when(customerMapper.selectOne(any())).thenReturn(null);
        when(leadMapper.selectActiveByCreditCode(anyString())).thenReturn(null);
        MarketingLeadInfo generatedLead = new MarketingLeadInfo();
        generatedLead.setId(1001L);
        when(leadEntryService.createDraft(any(LeadCreateRequest.class), eq("EMP_1"), eq("ORG_1")))
                .thenReturn(generatedLead);

        String headers = "线索类型,客户名称,统一社会信用代码,是否开户,客户号,所属行业,所属集团类型,所属集团名称,客户类型,是否基石客户,企业类型,客户标签,分配方式,指定客户经理范围,是否触达限制,客户说明,授信金额（万元）,授信敞口金额（万元）\n";
        String rows = "新客户开户线索,坏行,91320100ABC1234568,否,,,,,,否,,,全行公开认领,,maybe,,\n"
                + "新客户开户线索,好行,91320100ABC1234567,否,,,,,,否,,,全行公开认领,,否,,\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "lead-import.csv", "text/csv", (headers + rows).getBytes(StandardCharsets.UTF_8));

        var response = service.create(file, "EMP_1", "ORG_1");

        assertEquals("COMPLETED", response.getBatch().getImportStatus());
        assertEquals(1, response.getBatch().getErrorCount());
        assertEquals(1, response.getBatch().getGeneratedLeadCount());
        assertEquals("ERROR", insertedDetails.get(0).getValidationStatus());
        assertEquals("INVALID_TOUCH_RESTRICTED", insertedDetails.get(0).getErrorCode());
        assertEquals("PENDING", insertedDetails.get(0).getHandlingStatus());
        verify(leadEntryService).createDraft(any(LeadCreateRequest.class), eq("EMP_1"), eq("ORG_1"));
    }

    @Test
    void detailsKeepRejectedAndErrorsBeforeWarningsAndValidRows() {
        when(detailMapper.selectByBatchIdOrderByFailure(8L, 0, 20))
                .thenReturn(List.of(detail(1L, "REJECTED"), detail(2L, "ERROR"),
                        detail(3L, "WARNING"), detail(4L, "VALID")));
        when(detailMapper.countByBatchId(8L)).thenReturn(4L);

        var page = service.listDetails(8L, 1, 20);

        assertEquals(4, page.getRecords().size());
        assertEquals("REJECTED", page.getRecords().get(0).getValidationStatus());
        assertEquals("VALID", page.getRecords().get(3).getValidationStatus());
    }

    @Test
    void sourceFile_readsLocalStorageWithoutCallingObsFileApi() {
        MarketingLeadImportBatch batch = new MarketingLeadImportBatch();
        batch.setId(16L);
        batch.setRecordStatus("ACTIVE");
        batch.setImportEmpId("EMP_1");
        batch.setSourceFileId("local:source/20260827/source.csv");
        byte[] expected = "本地导入文件".getBytes(StandardCharsets.UTF_8);
        when(batchMapper.selectActiveById(16L)).thenReturn(batch);
        when(localStorage.supports(batch.getSourceFileId())).thenReturn(true);
        when(localStorage.read(batch.getSourceFileId())).thenReturn(expected);

        byte[] actual = service.sourceFile(16L, "EMP_1", false);

        assertArrayEquals(expected, actual);
        verify(localStorage).read(batch.getSourceFileId());
        verifyNoInteractions(fileApi);
    }

    @Test
    void preview_headerOnlyCsv_returnsSpecificNoDataReason() {
        stubBatchInsert(11L);
        String csv = "客户名称,统一社会信用代码,是否触达限制\n";

        var response = service.preview(csvFile(csv), "EMP_1", "ORG_1");

        assertEquals(1, response.getDetails().size());
        assertEquals("FILE_NO_DATA", response.getDetails().get(0).getErrorCode());
        assertEquals("文件没有可导入的数据行，请至少填写1条线索", response.getDetails().get(0).getErrorMessage());
    }

    @Test
    void preview_blankHeaderCsv_returnsSpecificMissingHeaderReason() {
        stubBatchInsert(12L);

        var response = service.preview(csvFile("\n"), "EMP_1", "ORG_1");

        assertEquals(1, response.getDetails().size());
        assertEquals("FILE_HEADER_MISSING", response.getDetails().get(0).getErrorCode());
        assertEquals("文件缺少表头，请下载最新线索导入模板", response.getDetails().get(0).getErrorMessage());
    }

    @Test
    void preview_missingCriticalHeader_namesTheMissingColumn() {
        stubBatchInsert(13L);
        String csv = "客户名称,是否触达限制\n客户甲,否\n";

        var response = service.preview(csvFile(csv), "EMP_1", "ORG_1");

        assertEquals(1, response.getDetails().size());
        assertEquals("FILE_REQUIRED_HEADER_MISSING", response.getDetails().get(0).getErrorCode());
        assertTrue(response.getDetails().get(0).getErrorMessage().contains("统一社会信用代码"));
    }

    @Test
    void preview_corruptedExcel_returnsSpecificCorruptionReason() {
        stubBatchInsert(14L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "lead-import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "not-an-excel-workbook".getBytes(StandardCharsets.UTF_8));

        var response = service.preview(file, "EMP_1", "ORG_1");

        assertEquals(1, response.getDetails().size());
        assertEquals("FILE_EXCEL_CORRUPTED", response.getDetails().get(0).getErrorCode());
        assertEquals("Excel文件损坏或格式无法读取，请重新保存为.xlsx后重试",
                response.getDetails().get(0).getErrorMessage());
    }

    @Test
    void preview_fileOverTenMb_isRejectedBeforeUploadWithSpecificReason() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lead-import.csv", "text/csv",
                new byte[10 * 1024 * 1024 + 1]);

        BizException exception = assertThrows(BizException.class,
                () -> service.preview(file, "EMP_1", "ORG_1"));

        assertEquals("MARKETING_LEAD_IMPORT_FILE_TOO_LARGE", exception.getCode());
        assertEquals("导入文件过大：单个文件不能超过10MB", exception.getMessage());
        verifyNoInteractions(localStorage, fileApi, batchMapper);
    }

    @Test
    void preview_csvErrorAfterBlankLine_keepsThePhysicalRowNumber() {
        stubBatchInsert(15L);
        String csv = "客户名称,统一社会信用代码,是否触达限制\n"
                + "\n"
                + "客户甲,91320100ABC1234567,maybe\n";

        var response = service.preview(csvFile(csv), "EMP_1", "ORG_1");

        assertEquals(1, response.getDetails().size());
        assertEquals(3, response.getDetails().get(0).getRowNo());
        assertEquals("INVALID_TOUCH_RESTRICTED", response.getDetails().get(0).getErrorCode());
    }

    private void stubBatchInsert(long batchId) {
        doAnswer(invocation -> {
            MarketingLeadImportBatch inserted = invocation.getArgument(0);
            inserted.setId(batchId);
            return 1;
        }).when(batchMapper).insert(any(MarketingLeadImportBatch.class));
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "lead-import.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private MarketingLeadImportDetail detail(long id, String status) {
        MarketingLeadImportDetail detail = new MarketingLeadImportDetail();
        detail.setId(id);
        detail.setBatchId(3L);
        detail.setValidationStatus(status);
        detail.setHandlingStatus("PENDING");
        if ("VALID".equals(status)) {
            detail.setCustName("测试企业");
            detail.setUnifiedCreditCode("91320100ABC1234567");
            detail.setRawRowJson("{custName=测试企业, unifiedCreditCode=91320100ABC1234567}");
        }
        return detail;
    }
}
