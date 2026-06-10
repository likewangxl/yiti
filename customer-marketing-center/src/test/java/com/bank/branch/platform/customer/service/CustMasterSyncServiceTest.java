package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CustMasterSyncService 单元测试（TDD Red 阶段）。
 * <p>
 * 验证客户信息同步：把 XAN_M98_CUST_STAT_SHOW3 指定统计日期、CUST_MASTER 中不存在的客户
 * 按 CUST_ID/CUST_NAME 去重插入；syncYesterday() 使用昨日日期。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class CustMasterSyncServiceTest {

    @Mock
    private CustMasterMapper custMasterMapper;

    @InjectMocks
    private CustMasterSyncService custMasterSyncService;

    @Test
    void syncByStatisDt_delegatesToMapperAndReturnsInsertedCount() {
        when(custMasterMapper.syncNewCustomersFromStat("2026-06-08")).thenReturn(3);

        int inserted = custMasterSyncService.syncByStatisDt("2026-06-08");

        assertThat(inserted).isEqualTo(3);
        verify(custMasterMapper).syncNewCustomersFromStat("2026-06-08");
    }

    @Test
    void syncYesterday_usesYesterdayDate() {
        String yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        when(custMasterMapper.syncNewCustomersFromStat(yesterday)).thenReturn(2);

        int inserted = custMasterSyncService.syncYesterday();

        assertThat(inserted).isEqualTo(2);
        ArgumentCaptor<String> dateCaptor = ArgumentCaptor.forClass(String.class);
        verify(custMasterMapper).syncNewCustomersFromStat(dateCaptor.capture());
        assertThat(dateCaptor.getValue()).isEqualTo(yesterday);
    }
}
