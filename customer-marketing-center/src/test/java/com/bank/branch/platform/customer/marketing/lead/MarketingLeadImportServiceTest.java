package com.bank.branch.platform.customer.marketing.lead;

import com.bank.branch.platform.customer.entity.marketing.MarketingLeadImportBatch;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadImportDetail;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadInfo;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadImportBatchMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadImportDetailMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadInfoMapper;
import com.bank.branch.platform.customer.service.marketing.MarketingLeadImportService;
import com.bank.branch.platform.governance.api.FileApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
    private MarketingCustomerInfoMapper customerMapper;
    @Mock
    private FileApi fileApi;

    @InjectMocks
    private MarketingLeadImportService service;

    @Test
    void waitingConfirmationProcessesOnlyValidRowsAndSkipsWarningRows() {
        MarketingLeadImportBatch batch = new MarketingLeadImportBatch();
        batch.setId(3L);
        batch.setImportStatus("WAITING_CONFIRM");
        batch.setRecordStatus("ACTIVE");
        batch.setImportEmpId("EMP_1");
        when(batchMapper.selectForUpdate(3L)).thenReturn(batch);

        MarketingLeadImportDetail valid = detail(31L, "VALID");
        MarketingLeadImportDetail warning = detail(32L, "WARNING");
        when(detailMapper.selectByBatchAndValidationStatus(3L, "VALID"))
                .thenReturn(List.of(valid));
        when(detailMapper.selectByBatchIdOrderByFailure(3L, 0, Integer.MAX_VALUE))
                .thenReturn(List.of(valid, warning));

        service.confirm(3L, "PROCESS_VALID", "EMP_1", "仅处理正常行");

        assertEquals("COMPLETED", batch.getImportStatus());
        assertEquals("PROCESS_VALID", batch.getConfirmAction());
        verify(detailMapper).updateHandlingIf(31L, "PENDING", "GENERATED", null);
        verify(detailMapper).updateHandlingIf(32L, "PENDING", "SKIPPED", null);
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

    private MarketingLeadImportDetail detail(long id, String status) {
        MarketingLeadImportDetail detail = new MarketingLeadImportDetail();
        detail.setId(id);
        detail.setBatchId(3L);
        detail.setValidationStatus(status);
        detail.setHandlingStatus("PENDING");
        return detail;
    }
}
