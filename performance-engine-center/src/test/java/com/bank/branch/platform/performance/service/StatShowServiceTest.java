package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.performance.mapper.StatShowMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StatShowService#getCustNameByCustId 单测：客户号查名（XAN_M98_CUST_STAT_SHOW3，LIMIT 1）。
 */
@ExtendWith(MockitoExtension.class)
class StatShowServiceTest {

    @Mock
    private StatShowMapper statShowMapper;

    @Mock
    private CustomerQueryApi customerQueryApi;

    @InjectMocks
    private StatShowService statShowService;

    @Test
    void getCustNameByCustId_hit_returnsName() {
        when(statShowMapper.selectCustNameByCustId("C001")).thenReturn("某某有限公司");

        Optional<String> name = statShowService.getCustNameByCustId("C001");

        assertThat(name).contains("某某有限公司");
        verify(statShowMapper).selectCustNameByCustId("C001");
    }

    @Test
    void getCustNameByCustId_trimsCustIdBeforeQuery() {
        when(statShowMapper.selectCustNameByCustId("C001")).thenReturn("某某有限公司");

        Optional<String> name = statShowService.getCustNameByCustId("  C001 ");

        assertThat(name).contains("某某有限公司");
        verify(statShowMapper).selectCustNameByCustId("C001");
    }

    @Test
    void getCustNameByCustId_noRow_returnsEmpty() {
        when(statShowMapper.selectCustNameByCustId("CX")).thenReturn(null);

        assertThat(statShowService.getCustNameByCustId("CX")).isEmpty();
    }

    @Test
    void getCustNameByCustId_blankName_returnsEmpty() {
        when(statShowMapper.selectCustNameByCustId("C001")).thenReturn("   ");

        assertThat(statShowService.getCustNameByCustId("C001")).isEmpty();
    }

    @Test
    void getCustNameByCustId_blankCustId_returnsEmpty_andNoQuery() {
        assertThat(statShowService.getCustNameByCustId("  ")).isEmpty();
        assertThat(statShowService.getCustNameByCustId(null)).isEmpty();
        verify(statShowMapper, never()).selectCustNameByCustId(org.mockito.ArgumentMatchers.any());
    }

    // ---- getCustNameFromMaster：客户名称改从客户主档 CUST_MASTER 查（CustomerQueryApi.getCustomerByCustNo）----

    @Test
    void getCustNameFromMaster_hit_returnsNameFromMaster() {
        CustomerDTO dto = new CustomerDTO();
        dto.setCustNo("C001");
        dto.setCustName("主档客户有限公司");
        when(customerQueryApi.getCustomerByCustNo("C001")).thenReturn(Optional.of(dto));

        Optional<String> name = statShowService.getCustNameFromMaster("  C001 ");

        assertThat(name).contains("主档客户有限公司");
        verify(customerQueryApi).getCustomerByCustNo("C001");
    }

    @Test
    void getCustNameFromMaster_notInMaster_returnsEmpty() {
        when(customerQueryApi.getCustomerByCustNo("CX")).thenReturn(Optional.empty());

        assertThat(statShowService.getCustNameFromMaster("CX")).isEmpty();
    }

    @Test
    void getCustNameFromMaster_blankCustNo_returnsEmpty_andNoQuery() {
        assertThat(statShowService.getCustNameFromMaster("  ")).isEmpty();
        assertThat(statShowService.getCustNameFromMaster(null)).isEmpty();
        verify(customerQueryApi, never()).getCustomerByCustNo(org.mockito.ArgumentMatchers.any());
    }
}
