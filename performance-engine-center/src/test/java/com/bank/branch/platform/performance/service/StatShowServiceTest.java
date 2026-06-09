package com.bank.branch.platform.performance.service;

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
}
