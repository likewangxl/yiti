package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.mapper.CustClaimMapper;
import com.bank.branch.platform.governance.api.DictApi;
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

    @Mock
    private OrgApi orgApi;

    @Mock
    private DictApi dictApi;

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

        when(claimMapper.selectPoolPage(isNull(), eq("E10001"), eq(0), eq(20))).thenReturn(mockList);
        when(claimMapper.countPoolPage(isNull(), eq("E10001"))).thenReturn(2L);

        // when
        PageResult<CustMaster> result = customerPoolService.listPool(null, "E10001", 1, 20);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getPageNo()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(20);
        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getRecords().get(0).getCustName()).isEqualTo("测试客户A");

        verify(claimMapper).selectPoolPage(null, "E10001", 0, 20);
        verify(claimMapper).countPoolPage(null, "E10001");
    }

    @Test
    void listPool_shouldCalculateOffset() {
        // given: pageNo=3, pageSize=10 -> offset = (3-1)*10 = 20
        CustMaster c1 = new CustMaster();
        c1.setId("cust-021");
        c1.setCustName("测试客户C");

        when(claimMapper.selectPoolPage(eq("关键词"), eq("E10001"), eq(20), eq(10)))
                .thenReturn(Collections.singletonList(c1));
        when(claimMapper.countPoolPage(eq("关键词"), eq("E10001"))).thenReturn(25L);

        // when
        PageResult<CustMaster> result = customerPoolService.listPool("关键词", "E10001", 3, 10);

        // then
        assertThat(result.getPageNo()).isEqualTo(3);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getTotal()).isEqualTo(25L);
        assertThat(result.getRecords()).hasSize(1);

        // 验证 offset 计算正确：(3-1)*10=20
        verify(claimMapper).selectPoolPage("关键词", "E10001", 20, 10);
        verify(claimMapper).countPoolPage("关键词", "E10001");
    }

    @Test
    void listPoolAsDTO_shouldReturnChineseIndustryCustomerTypeAndSourceOrg() {
        CustMaster customer = new CustMaster();
        customer.setId("cust-001");
        customer.setIndustry("IT");
        customer.setCustomerType("CORP");
        customer.setOwnerOrgId("ORG001");
        when(claimMapper.selectPoolPage(null, "E10001", 0, 20)).thenReturn(List.of(customer));
        when(claimMapper.countPoolPage(null, "E10001")).thenReturn(1L);
        when(dictApi.getDictLabel("INDUSTRY", "IT")).thenReturn("信息技术业");
        when(dictApi.getDictLabel("CUSTOMER_TYPE", "CORP")).thenReturn("公司客户");
        OrgDTO org = new OrgDTO();
        org.setOrgCode("ORG001");
        org.setOrgName("西安分行营业部");
        when(orgApi.getOrgsByCodes(List.of("ORG001"))).thenReturn(List.of(org));

        PageResult<CustomerDTO> result = customerPoolService.listPoolAsDTO(
                null, "E10001", 1, 20);

        assertThat(result.getRecords()).singleElement()
                .extracting(CustomerDTO::getIndustryName,
                        CustomerDTO::getCustomerTypeName,
                        CustomerDTO::getOwnerOrgName)
                .containsExactly("信息技术业", "公司客户", "西安分行营业部");
    }
}
