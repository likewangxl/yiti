package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.entity.CustTagRel;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.customer.mapper.CustTagMapper;
import com.bank.branch.platform.customer.mapper.CustTagRelMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TagCustomerService.listCustomersForExport 单元测试（TDD Red 阶段）。
 * <p>
 * 验证按标签 ID 导出客户主档数据的查询逻辑。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TagCustomerServiceExportTest {

    @Mock
    private CustTagMapper tagMapper;

    @Mock
    private CustTagRelMapper tagRelMapper;

    @Mock
    private CustMasterMapper masterMapper;

    @InjectMocks
    private TagCustomerService tagCustomerService;

    /**
     * listCustomersForExport：正常场景，按 tagId 查询客户列表。
     */
    @Test
    void listCustomersForExport_shouldReturnCustomerMasterList() {
        // given: 标签关联两个客户 ID
        List<String> custIds = Arrays.asList("cust-001", "cust-002");
        when(tagRelMapper.selectCustIdsByTagId(eq("tag-001"))).thenReturn(custIds);

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

        when(masterMapper.selectByIds(eq(custIds))).thenReturn(Arrays.asList(c1, c2));

        // when
        List<CustMaster> result = tagCustomerService.listCustomersForExport("tag-001");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCustNo()).isEqualTo("CUST_00001");
        assertThat(result.get(1).getCustNo()).isEqualTo("CUST_00002");

        verify(tagRelMapper).selectCustIdsByTagId("tag-001");
        verify(masterMapper).selectByIds(custIds);
    }

    /**
     * listCustomersForExport：标签下无客户时，返回空列表（不调用 masterMapper）。
     */
    @Test
    void listCustomersForExport_shouldReturnEmptyWhenNoCustomers() {
        // given
        when(tagRelMapper.selectCustIdsByTagId(eq("tag-empty"))).thenReturn(List.of());

        // when
        List<CustMaster> result = tagCustomerService.listCustomersForExport("tag-empty");

        // then
        assertThat(result).isEmpty();
    }
}
