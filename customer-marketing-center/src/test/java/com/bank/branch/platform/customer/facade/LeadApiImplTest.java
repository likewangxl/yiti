package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LeadApiImpl 单元测试（TDD）
 * 验证委托调用路径正确。
 */
@ExtendWith(MockitoExtension.class)
class LeadApiImplTest {

    @Mock
    private CustLeadMapper leadMapper;

    @InjectMocks
    private LeadApiImpl leadApiImpl;

    @Test
    void getById_shouldDelegateToCustLeadMapper() {
        // given
        CustLead lead = new CustLead();
        lead.setId("lead-001");
        lead.setLeadNo("LEAD_20240101_0001");
        lead.setCustName("测试公司");
        when(leadMapper.selectById("lead-001")).thenReturn(lead);

        // when
        CustLead result = leadApiImpl.getById("lead-001");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("lead-001");
        assertThat(result.getCustName()).isEqualTo("测试公司");
        verify(leadMapper).selectById("lead-001");
    }

    @Test
    void getById_shouldReturnNullWhenNotFound() {
        // given
        when(leadMapper.selectById("not-exist")).thenReturn(null);

        // when
        CustLead result = leadApiImpl.getById("not-exist");

        // then
        assertThat(result).isNull();
        verify(leadMapper).selectById("not-exist");
    }

    @Test
    void getByLeadNo_shouldDelegateToCustLeadMapper() {
        // given
        CustLead lead = new CustLead();
        lead.setId("lead-001");
        lead.setLeadNo("LEAD_20240101_0001");
        lead.setCustName("测试公司");
        when(leadMapper.selectByLeadNo("LEAD_20240101_0001")).thenReturn(lead);

        // when
        CustLead result = leadApiImpl.getByLeadNo("LEAD_20240101_0001");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getLeadNo()).isEqualTo("LEAD_20240101_0001");
        verify(leadMapper).selectByLeadNo("LEAD_20240101_0001");
    }
}
