package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.mapper.CustClaimMapper;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CustomerService.listAllForExport 单元测试（TDD Red 阶段）。
 * <p>
 * 验证导出方法正确调用 Mapper 分页接口并返回结果列表。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceExportTest {

    @Mock
    private CustMasterMapper masterMapper;

    @Mock
    private CustClaimMapper claimMapper;

    @Mock
    private CustLeadMapper leadMapper;

    @Mock
    private TouchTaskMapper touchTaskMapper;

    @Mock
    private WorkflowApi workflowApi;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CustomerService customerService;

    /**
     * listAllForExport：正常场景，验证 offset=0, limit=maxRows，返回结果列表。
     */
    @Test
    void listAllForExport_shouldReturnMasterList() {
        // given
        CustMaster c1 = new CustMaster();
        c1.setId("cust-001");
        c1.setCustNo("CUST_00001");
        c1.setCustName("深圳科技有限公司");
        c1.setUnifiedCreditCode("91440300XXXXXXXXX1");
        c1.setStatus("ACTIVE");

        CustMaster c2 = new CustMaster();
        c2.setId("cust-002");
        c2.setCustNo("CUST_00002");
        c2.setCustName("广州贸易股份有限公司");
        c2.setUnifiedCreditCode("91440100XXXXXXXXX2");
        c2.setStatus("ACTIVE");

        // maxRows=10000 → offset=0, limit=10000
        when(masterMapper.selectPage(isNull(), isNull(), eq(0), eq(10000)))
                .thenReturn(Arrays.asList(c1, c2));

        // when
        List<CustMaster> result = customerService.listAllForExport(null, null, 10000);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCustNo()).isEqualTo("CUST_00001");
        assertThat(result.get(1).getCustNo()).isEqualTo("CUST_00002");

        // 验证 offset=0, limit=10000
        verify(masterMapper).selectPage(null, null, 0, 10000);
    }

    /**
     * listAllForExport：带 keyword 和 status 过滤，正确透传给 Mapper。
     */
    @Test
    void listAllForExport_shouldPassFilterParams() {
        // given
        CustMaster c1 = new CustMaster();
        c1.setId("cust-001");
        c1.setCustNo("CUST_00001");
        c1.setCustName("深圳科技有限公司");
        c1.setStatus("ACTIVE");

        when(masterMapper.selectPage(eq("深圳"), eq("ACTIVE"), eq(0), eq(5000)))
                .thenReturn(List.of(c1));

        // when
        List<CustMaster> result = customerService.listAllForExport("深圳", "ACTIVE", 5000);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCustName()).isEqualTo("深圳科技有限公司");

        verify(masterMapper).selectPage("深圳", "ACTIVE", 0, 5000);
    }

    /**
     * listAllForExport：空结果时返回空列表。
     */
    @Test
    void listAllForExport_shouldReturnEmptyListWhenNoData() {
        // given
        when(masterMapper.selectPage(isNull(), isNull(), eq(0), eq(10000)))
                .thenReturn(List.of());

        // when
        List<CustMaster> result = customerService.listAllForExport(null, null, 10000);

        // then
        assertThat(result).isEmpty();
    }
}
