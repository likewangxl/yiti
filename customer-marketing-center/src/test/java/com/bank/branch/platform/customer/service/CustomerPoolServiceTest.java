package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.mapper.CustClaimMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CustomerPoolService 单元测试（TDD RED 阶段）
 * 使用 MockitoExtension，不需要 Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
class CustomerPoolServiceTest {

    @Mock
    private CustClaimMapper claimMapper;

    @InjectMocks
    private CustomerPoolService customerPoolService;

    // ==================== listPool ====================

    @Test
    void listPool_shouldReturnUnclaimedCustomers() {
        // given: 客户池返回 2 条未认领客户
        CustMaster c1 = new CustMaster();
        c1.setId("cust-001");
        c1.setCustName("测试客户A");
        CustMaster c2 = new CustMaster();
        c2.setId("cust-002");
        c2.setCustName("测试客户B");
        List<CustMaster> mockList = Arrays.asList(c1, c2);

        when(claimMapper.selectPoolPage(isNull(), eq(0), eq(20))).thenReturn(mockList);
        when(claimMapper.countPoolPage(isNull())).thenReturn(2L);

        // when
        PageResult<CustMaster> result = customerPoolService.listPool(null, 1, 20);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getPageNo()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(20);
        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getRecords().get(0).getCustName()).isEqualTo("测试客户A");

        verify(claimMapper).selectPoolPage(null, 0, 20);
        verify(claimMapper).countPoolPage(null);
    }

    @Test
    void listPool_shouldCalculateOffset() {
        // given: pageNo=3, pageSize=10 -> offset = (3-1)*10 = 20
        CustMaster c1 = new CustMaster();
        c1.setId("cust-021");
        c1.setCustName("测试客户C");

        when(claimMapper.selectPoolPage(eq("关键词"), eq(20), eq(10)))
                .thenReturn(Collections.singletonList(c1));
        when(claimMapper.countPoolPage(eq("关键词"))).thenReturn(25L);

        // when
        PageResult<CustMaster> result = customerPoolService.listPool("关键词", 3, 10);

        // then
        assertThat(result.getPageNo()).isEqualTo(3);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getTotal()).isEqualTo(25L);
        assertThat(result.getRecords()).hasSize(1);

        // 验证 offset 计算正确：(3-1)*10=20
        verify(claimMapper).selectPoolPage("关键词", 20, 10);
        verify(claimMapper).countPoolPage("关键词");
    }
}
