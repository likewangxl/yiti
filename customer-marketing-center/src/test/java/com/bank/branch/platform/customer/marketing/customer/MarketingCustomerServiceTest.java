package com.bank.branch.platform.customer.marketing.customer;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerProfileUpdateRequest;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerQuery;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerVO;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.service.marketing.MarketingCustomerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingCustomerServiceTest {

    @Mock
    MarketingCustomerInfoMapper customerMapper;
    @Mock
    UserApi userApi;
    @Mock
    OrgApi orgApi;
    @Mock
    BizScopeApi bizScopeApi;

    @Test
    void listMine_shouldAlwaysConstrainToCurrentMainManager() {
        MarketingCustomerQuery query = new MarketingCustomerQuery();
        query.setKeyword("测试");
        MarketingCustomerInfo customer = customer(1L, "E10001", "ORG001");
        when(customerMapper.selectPage(any(), eq(DataScopeType.SELF.getCode()), any(), eq("E10001"),
                eq(0), eq(20))).thenReturn(List.of(customer));
        when(customerMapper.countPage(any(), eq(DataScopeType.SELF.getCode()), any(), eq("E10001")))
                .thenReturn(1L);

        MarketingCustomerService service = service();
        PageResult<MarketingCustomerVO> result = service.listMine(query, "E10001", "ORG001");

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(1L);
            assertThat(item.getMainManagerId()).isEqualTo("E10001");
        });
        ArgumentCaptor<MarketingCustomerQuery> captor = ArgumentCaptor.forClass(MarketingCustomerQuery.class);
        verify(customerMapper).selectPage(captor.capture(), eq(DataScopeType.SELF.getCode()), any(), eq("E10001"),
                eq(0), eq(20));
        assertThat(captor.getValue().getOwnershipStatus()).isEqualTo("ASSIGNED");
        assertThat(captor.getValue().getRecordStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void listAll_shouldReturnUnassignedCustomerWhenManagerAndOrgAreMissing() {
        MarketingCustomerInfo customer = customer(5L, null, null);
        customer.setOwnershipStatus("UNASSIGNED");
        when(customerMapper.selectPage(any(), eq(DataScopeType.ALL.getCode()), any(), isNull(),
                eq(0), eq(20))).thenReturn(List.of(customer));
        when(customerMapper.countPage(any(), eq(DataScopeType.ALL.getCode()), any(), isNull()))
                .thenReturn(1L);

        PageResult<MarketingCustomerVO> result = service().listAll(new MarketingCustomerQuery(),
                "ADMIN", "ORG001", DataScopeType.ALL);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getOwnershipStatus()).isEqualTo("UNASSIGNED");
            assertThat(item.getMainManagerId()).isNull();
            assertThat(item.getMainManagerName()).isNull();
            assertThat(item.getMainOrgId()).isNull();
            assertThat(item.getMainOrgName()).isNull();
        });
    }

    @Test
    void getDetail_shouldRejectCustomerOutsideScope() {
        MarketingCustomerInfo customer = customer(2L, "OTHER", "ORG002");
        when(customerMapper.selectActiveById(2L)).thenReturn(customer);
        when(orgApi.getOrgSubtreeCodes("ORG001")).thenReturn(Set.of("ORG001"));

        MarketingCustomerService service = service();

        assertThatThrownBy(() -> service.getDetail(2L, "E10001", "ORG001",
                DataScopeType.ORG_SUBTREE, false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权");
    }

    @Test
    void updateProfile_shouldUseProfileAndLockVersionCasAndOnlyPersistAllowedFields() {
        MarketingCustomerInfo customer = customer(3L, "E10001", "ORG001");
        customer.setProfileVersion(4);
        customer.setLockVersion(7);
        when(customerMapper.selectActiveById(3L)).thenReturn(customer);
        when(customerMapper.updateProfileByVersions(eq(3L), eq(4), eq(7), any(), eq("ADMIN"), any()))
                .thenReturn(1);
        when(customerMapper.selectActiveById(3L)).thenReturn(customer, customer);

        MarketingCustomerProfileUpdateRequest request = new MarketingCustomerProfileUpdateRequest();
        request.setCustName("新名称");
        request.setCreditExposureAmount(new BigDecimal("120.50"));
        request.setProfileVersion(4);
        request.setLockVersion(7);
        request.setReason("管理员修订客户资料");

        MarketingCustomerService service = service();
        MarketingCustomerVO result = service.updateProfile(3L, request, "ADMIN", "ORG001", true);

        assertThat(result.getId()).isEqualTo(3L);
        ArgumentCaptor<MarketingCustomerProfileUpdateRequest> captor =
                ArgumentCaptor.forClass(MarketingCustomerProfileUpdateRequest.class);
        verify(customerMapper).updateProfileByVersions(eq(3L), eq(4), eq(7), captor.capture(), eq("ADMIN"), any());
        assertThat(captor.getValue().getCustName()).isEqualTo("新名称");
        assertThat(captor.getValue().getCreditExposureAmount()).isEqualByComparingTo("120.50");
    }

    @Test
    void updateProfile_shouldRejectStaleVersions() {
        MarketingCustomerInfo customer = customer(4L, "E10001", "ORG001");
        customer.setProfileVersion(5);
        customer.setLockVersion(8);
        when(customerMapper.selectActiveById(4L)).thenReturn(customer);

        MarketingCustomerProfileUpdateRequest request = new MarketingCustomerProfileUpdateRequest();
        request.setCustName("新名称");
        request.setProfileVersion(4);
        request.setLockVersion(8);
        request.setReason("管理员修订客户资料");

        assertThatThrownBy(() -> service().updateProfile(4L, request, "ADMIN", "ORG001", true))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("版本");
    }

    private MarketingCustomerService service() {
        return new MarketingCustomerService(customerMapper, userApi, orgApi, bizScopeApi);
    }

    private MarketingCustomerInfo customer(Long id, String managerId, String orgId) {
        MarketingCustomerInfo customer = new MarketingCustomerInfo();
        customer.setId(id);
        customer.setCustNo("C" + id);
        customer.setCustName("测试客户" + id);
        customer.setUnifiedCreditCode("91310000000000000" + id);
        customer.setMainManagerId(managerId);
        customer.setMainOrgId(orgId);
        customer.setOwnershipStatus("ASSIGNED");
        customer.setOwnershipMaintainMode("AUTO");
        customer.setRecordStatus("ACTIVE");
        customer.setProfileVersion(1);
        customer.setLockVersion(1);
        return customer;
    }
}
