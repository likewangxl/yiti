package com.bank.branch.platform.customer.marketing.tag;

import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagImportBatchMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagImportDetailMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadInfoMapper;
import com.bank.branch.platform.customer.service.marketing.MarketingCustomerTagImportService;
import com.bank.branch.platform.customer.service.marketing.MarketingCustomerTagService;
import com.bank.branch.platform.governance.api.FileApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class MarketingCustomerTagImportServiceTest {
    @Mock private MarketingCustomerTagImportBatchMapper batchMapper;
    @Mock private MarketingCustomerTagImportDetailMapper detailMapper;
    @Mock private MarketingCustomerInfoMapper customerMapper;
    @Mock private MarketingLeadInfoMapper leadMapper;
    @Mock private MarketingCustomerTagService tagService;
    @Mock private FileApi fileApi;
    @InjectMocks private MarketingCustomerTagImportService service;

    @Test
    void previewRejectsUnsupportedImportMode() {
        assertThatThrownBy(() -> service.preview(null, 1L, "MERGE"))
                .hasMessageContaining("APPEND");
    }
}
