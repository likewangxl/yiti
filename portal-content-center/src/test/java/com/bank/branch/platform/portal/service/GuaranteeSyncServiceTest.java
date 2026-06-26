package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.portal.mapper.GuaranteeSyncMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GuaranteeSyncService 单元测试（定时任务同步逻辑）。
 */
@ExtendWith(MockitoExtension.class)
class GuaranteeSyncServiceTest {

    @Mock
    private GuaranteeSyncMapper guaranteeSyncMapper;

    @InjectMocks
    private GuaranteeSyncService guaranteeSyncService;

    @Test
    void processing_clmsCountZero_shortCircuitsWithoutWrites() {
        when(guaranteeSyncMapper.getClmsCount(anyString())).thenReturn(0);

        guaranteeSyncService.processing();

        // 当日无 clms 数据：只查计数，绝不更新/插入
        verify(guaranteeSyncMapper).getClmsCount(anyString());
        verify(guaranteeSyncMapper, never()).updateToGuarantee(anyString());
        verify(guaranteeSyncMapper, never()).saveToGuarantee(anyString());
        verify(guaranteeSyncMapper, never()).saveToCcmsBusiness(anyString());
        verify(guaranteeSyncMapper, never()).updateToGuaranteeUpdate();
    }

    @Test
    void processing_clmsCountPositive_runsFourSqlsInOrder() {
        when(guaranteeSyncMapper.getClmsCount(anyString())).thenReturn(5);

        guaranteeSyncService.processing();

        // 顺序：先更新已存在客户 → 再插入新客户 → 归集合同 → 刷新 update_time
        var io = inOrder(guaranteeSyncMapper);
        io.verify(guaranteeSyncMapper).getClmsCount(anyString());
        io.verify(guaranteeSyncMapper).updateToGuarantee(anyString());
        io.verify(guaranteeSyncMapper).saveToGuarantee(anyString());
        io.verify(guaranteeSyncMapper).saveToCcmsBusiness(anyString());
        io.verify(guaranteeSyncMapper).updateToGuaranteeUpdate();
    }
}
