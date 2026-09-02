package com.bank.branch.platform.customer.marketing.tag;

import com.bank.branch.platform.customer.dto.marketing.tag.TagCustomerBatchApprovalRequest;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTag;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTagImportBatch;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTagImportDetail;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagImportBatchMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagImportDetailMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagRelMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadInfoMapper;
import com.bank.branch.platform.customer.service.marketing.MarketingCustomerTagApprovalService;
import com.bank.branch.platform.customer.service.marketing.MarketingCustomerTagService;
import com.bank.branch.platform.customer.service.marketing.TagApprovalRequiredException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingCustomerTagApprovalServiceTest {

    @Mock private MarketingCustomerTagMapper tagMapper;
    @Mock private MarketingCustomerTagImportBatchMapper batchMapper;
    @Mock private MarketingCustomerTagImportDetailMapper detailMapper;
    @Mock private MarketingCustomerTagRelMapper relationMapper;
    @Mock private MarketingCustomerInfoMapper customerMapper;
    @Mock private MarketingLeadInfoMapper leadMapper;
    @Mock private MarketingCustomerTagService tagService;
    @InjectMocks private MarketingCustomerTagApprovalService service;

    @Test
    void customerApprovalRequiresExplicitTagApprovalConfirmation() {
        MarketingCustomerTag tag = new MarketingCustomerTag();
        tag.setId(7L);
        tag.setTagName("园区客户");
        tag.setApprovalStatus("PENDING");
        MarketingCustomerTagImportBatch batch = new MarketingCustomerTagImportBatch();
        batch.setId(8L);
        batch.setTagId(7L);
        MarketingCustomerTagImportDetail detail = new MarketingCustomerTagImportDetail();
        detail.setId(9L);
        detail.setBatchId(8L);
        detail.setApprovalStatus("PENDING");
        detail.setValidationStatus("VALID");

        when(batchMapper.selectById(8L)).thenReturn(batch);
        when(tagService.requireTag(7L)).thenReturn(tag);
        when(detailMapper.selectByBatchIdAndIds(8L, List.of(9L))).thenReturn(List.of(detail));

        TagCustomerBatchApprovalRequest request = new TagCustomerBatchApprovalRequest();
        request.setBatchId(8L);
        request.setDetailIds(List.of(9L));

        assertThatThrownBy(() -> service.approve(request, "E001"))
                .isInstanceOf(TagApprovalRequiredException.class);
    }
}
