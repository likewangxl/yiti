package com.bank.branch.platform.yundun.service;

import com.alibaba.excel.annotation.ExcelProperty;
import com.bank.branch.platform.yundun.dto.AccountabilityViolationQuery;
import com.bank.branch.platform.yundun.dto.AccountabilityViolationSaveReq;
import com.bank.branch.platform.yundun.dto.CreditViolationQuery;
import com.bank.branch.platform.yundun.dto.CreditViolationSaveReq;
import com.bank.branch.platform.yundun.entity.AccountabilityViolation;
import com.bank.branch.platform.yundun.entity.CreditViolation;
import com.bank.branch.platform.yundun.mapper.AccountabilityViolationMapper;
import com.bank.branch.platform.yundun.mapper.CreditViolationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 云盾两类违规信息导出契约测试。 */
@ExtendWith(MockitoExtension.class)
class ViolationExportContractTest {

    @Mock
    private AccountabilityViolationMapper accountabilityMapper;

    @Mock
    private CreditViolationMapper creditMapper;

    @Test
    void exportModelsMustNotExposeInUseExcelColumns() throws Exception {
        Field accountabilityInUse = AccountabilityViolationSaveReq.class.getDeclaredField("inUse");
        Field creditInUse = CreditViolationSaveReq.class.getDeclaredField("inUse");

        assertThat(accountabilityInUse.getAnnotation(ExcelProperty.class)).isNull();
        assertThat(creditInUse.getAnnotation(ExcelProperty.class)).isNull();
    }

    @Test
    void accountabilityExportByFilterMustQueryAndReturnAvailableRowsOnly() {
        AccountabilityViolation available = accountability(1L, 1);
        AccountabilityViolation unavailable = accountability(2L, 0);
        when(accountabilityMapper.selectList(any())).thenReturn(List.of(available, unavailable));

        List<AccountabilityViolationSaveReq> rows = new AccountabilityViolationService(accountabilityMapper)
                .exportRows(new AccountabilityViolationQuery(), null);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AccountabilityViolation>>
                wrapperCaptor = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(accountabilityMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase()).contains("in_use");
        assertThat(rows).extracting(AccountabilityViolationSaveReq::getInUse).containsExactly(1);
    }

    @Test
    void accountabilityExportByIdsMustQueryAndReturnAvailableRowsOnly() {
        AccountabilityViolation available = accountability(1L, 1);
        AccountabilityViolation unavailable = accountability(2L, 0);
        when(accountabilityMapper.selectList(any())).thenReturn(List.of(available, unavailable));

        List<AccountabilityViolationSaveReq> rows = new AccountabilityViolationService(accountabilityMapper)
                .exportRows(new AccountabilityViolationQuery(), List.of(1L, 2L));

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AccountabilityViolation>>
                wrapperCaptor = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(accountabilityMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase()).contains("id", "in_use");
        assertThat(rows).extracting(AccountabilityViolationSaveReq::getInUse).containsExactly(1);
    }

    @Test
    void creditExportByFilterMustQueryAndReturnAvailableRowsOnly() {
        CreditViolation available = credit(1L, 1);
        CreditViolation unavailable = credit(2L, 0);
        when(creditMapper.selectList(any())).thenReturn(List.of(available, unavailable));

        List<CreditViolationSaveReq> rows = new CreditViolationService(creditMapper)
                .exportRows(new CreditViolationQuery(), null);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CreditViolation>> wrapperCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(creditMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase()).contains("in_use");
        assertThat(rows).extracting(CreditViolationSaveReq::getInUse).containsExactly(1);
    }

    @Test
    void creditExportByIdsMustQueryAndReturnAvailableRowsOnly() {
        CreditViolation available = credit(1L, 1);
        CreditViolation unavailable = credit(2L, 0);
        when(creditMapper.selectList(any())).thenReturn(List.of(available, unavailable));

        List<CreditViolationSaveReq> rows = new CreditViolationService(creditMapper)
                .exportRows(new CreditViolationQuery(), List.of(1L, 2L));

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CreditViolation>> wrapperCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(creditMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase()).contains("id", "in_use");
        assertThat(rows).extracting(CreditViolationSaveReq::getInUse).containsExactly(1);
    }

    private AccountabilityViolation accountability(Long id, Integer inUse) {
        AccountabilityViolation row = new AccountabilityViolation();
        row.setId(id);
        row.setInUse(inUse);
        return row;
    }

    private CreditViolation credit(Long id, Integer inUse) {
        CreditViolation row = new CreditViolation();
        row.setId(id);
        row.setInUse(inUse);
        return row;
    }
}
