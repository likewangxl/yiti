package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadTagRel;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerClaimMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadTagRelMapper;
import com.bank.branch.platform.governance.api.DictApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 目标营销表待认领池查询契约。 */
@ExtendWith(MockitoExtension.class)
class MarketingCustomerPoolServiceTest {

    @Mock
    private MarketingCustomerClaimMapper claimMapper;
    @Mock
    private DictApi dictApi;
    @Mock
    private OrgApi orgApi;
    @Mock
    private MarketingLeadTagRelMapper leadTagRelMapper;

    @InjectMocks
    private MarketingCustomerPoolService service;

    @Test
    void listAvailable_shouldReadMarketingPoolAndTranslateDisplayNames() {
        CustomerDTO row = new CustomerDTO();
        row.setId("101");
        row.setCustName("目标客户");
        row.setIndustry("MANUFACTURING");
        row.setCustomerType("CORPORATE");
        row.setGroupType("GROUP_CUSTOMER");
        row.setEnterpriseType("STATE_OWNED");
        row.setSourceLeadId("51");
        row.setOwnerOrgId("ORG-1");
        when(claimMapper.selectAvailablePoolPage(eq("目标"), eq("EMP-1"), eq(20), eq(20)))
                .thenReturn(List.of(row));
        when(claimMapper.countAvailablePoolPage("目标", "EMP-1")).thenReturn(1L);
        when(dictApi.getDictLabel("INDUSTRY", "MANUFACTURING")).thenReturn("制造业");
        when(dictApi.getDictLabel("CUSTOMER_TYPE", "CORPORATE")).thenReturn("公司客户");
        when(dictApi.getDictLabel("GROUP_TYPE", "GROUP_CUSTOMER")).thenReturn("集团客户");
        when(dictApi.getDictLabel("ENTERPRISE_TYPE", "STATE_OWNED")).thenReturn("国有企业");
        MarketingLeadTagRel tag = new MarketingLeadTagRel();
        tag.setLeadId(51L);
        tag.setTagNameSnapshot("重点拓展");
        when(leadTagRelMapper.selectList(any())).thenReturn(List.of(tag));
        OrgDTO org = new OrgDTO();
        org.setOrgCode("ORG-1");
        org.setOrgName("一支行");
        when(orgApi.getOrgsByCodes(List.of("ORG-1"))).thenReturn(List.of(org));

        PageResult<CustomerDTO> result = service.listAvailable("目标", "EMP-1", 2, 20);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getIndustryName()).isEqualTo("制造业");
            assertThat(item.getCustomerTypeName()).isEqualTo("公司客户");
            assertThat(item.getGroupTypeName()).isEqualTo("集团客户");
            assertThat(item.getEnterpriseTypeName()).isEqualTo("国有企业");
            assertThat(item.getTagNames()).containsExactly("重点拓展");
            assertThat(item.getOwnerOrgName()).isEqualTo("一支行");
        });
        verify(claimMapper).selectAvailablePoolPage("目标", "EMP-1", 20, 20);
    }
}
