package com.bank.branch.platform.customer.marketing.tag;

import com.bank.branch.platform.customer.dto.marketing.tag.TagCreateRequest;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTag;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagRelMapper;
import com.bank.branch.platform.customer.service.marketing.MarketingCustomerTagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingCustomerTagServiceTest {

    @Mock
    private MarketingCustomerTagMapper tagMapper;
    @Mock
    private MarketingCustomerTagRelMapper relationMapper;

    @InjectMocks
    private MarketingCustomerTagService service;

    @Test
    void create_shouldStartPendingAndDisabledTag() {
        when(tagMapper.selectByTagName("战略客户")).thenReturn(null);
        when(tagMapper.insert(any(MarketingCustomerTag.class))).thenAnswer(invocation -> {
            MarketingCustomerTag tag = invocation.getArgument(0);
            tag.setId(10L);
            return 1;
        });

        TagCreateRequest request = new TagCreateRequest();
        request.setTagName("战略客户");
        request.setTagCategory("价值类");
        request.setTagPriority(10);

        MarketingCustomerTag result = service.create(request, "E001", "ORG001");

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getApprovalStatus()).isEqualTo("PENDING");
        assertThat(result.getStatus()).isEqualTo("DISABLED");
        verify(tagMapper).insert(any(MarketingCustomerTag.class));
    }

    @Test
    void listDelegatesExactTagTypeAndNormalizedViewStatusToCountAndPage() {
        when(tagMapper.countPage("高净值", "价值类", "RULE", "ENABLED", "APPROVED", "ACTIVE"))
                .thenReturn(1L);
        when(tagMapper.selectPage("高净值", "价值类", "RULE", "ENABLED", "APPROVED", "ACTIVE", 0, 100))
                .thenReturn(List.of(new MarketingCustomerTag()));

        var result = service.list(" 高净值 ", " 价值类 ", " RULE ", " ENABLED ",
                " APPROVED ", " active ", 0, 200);

        assertThat(result.getTotal()).isEqualTo(1L);
        verify(tagMapper).countPage("高净值", "价值类", "RULE", "ENABLED", "APPROVED", "ACTIVE");
        verify(tagMapper).selectPage("高净值", "价值类", "RULE", "ENABLED", "APPROVED", "ACTIVE", 0, 100);
    }
}
