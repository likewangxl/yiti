package com.bank.branch.platform.customer.marketing.customer;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerOwnershipRestoreRequest;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerTransferRequest;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTransferLog;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTransferLogMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTransferTargetMapper;
import com.bank.branch.platform.customer.service.marketing.CustomerOwnershipService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerOwnershipServiceTest {

    @Mock
    MarketingCustomerInfoMapper customerMapper;
    @Mock
    MarketingCustomerTransferLogMapper transferLogMapper;
    @Mock
    MarketingCustomerTransferTargetMapper transferTargetMapper;
    @Mock
    UserApi userApi;

    @Test
    void transfer_shouldPersistManualOwnershipAndPrimaryTarget() {
        MarketingCustomerInfo customer = customer(10L, "OLD", "ORG001");
        customer.setLockVersion(2);
        when(customerMapper.selectActiveById(10L)).thenReturn(customer);
        when(userApi.getUserRoleCodes("NEW")).thenReturn(Set.of("R_RM"));
        when(userApi.getUserByEmpId("NEW")).thenReturn(user("NEW", "ORG002"));
        when(customerMapper.updateOwnershipByLockVersion(eq(10L), eq(2), eq("NEW"), eq("ORG002"),
                eq("ASSIGNED"), eq("MANUAL"), eq("ADMIN"), any(), eq("岗位调整"))).thenReturn(1);
        when(transferLogMapper.insert(any(MarketingCustomerTransferLog.class))).thenAnswer(invocation -> {
            MarketingCustomerTransferLog log = invocation.getArgument(0);
            log.setId(100L);
            return 1;
        });
        when(transferTargetMapper.insert(any(com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTransferTarget.class)))
                .thenReturn(1);

        MarketingCustomerTransferRequest request = new MarketingCustomerTransferRequest();
        request.setTransferAction("TRANSFER");
        request.setTargetManagerId("NEW");
        request.setReason("岗位调整");
        request.setLockVersion(2);

        CustomerOwnershipService service = service();
        service.transfer(10L, request, "ADMIN", "ORG001", true);

        ArgumentCaptor<MarketingCustomerTransferLog> logCaptor =
                ArgumentCaptor.forClass(MarketingCustomerTransferLog.class);
        verify(transferLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getTransferAction()).isEqualTo("TRANSFER");
        assertThat(logCaptor.getValue().getPrimaryToManagerId()).isEqualTo("NEW");
        verify(transferTargetMapper).insert(any(com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTransferTarget.class));
        verify(customerMapper).updateOwnershipByLockVersion(eq(10L), eq(2), eq("NEW"), eq("ORG002"),
                eq("ASSIGNED"), eq("MANUAL"), eq("ADMIN"), any(), eq("岗位调整"));
    }

    @Test
    void transfer_shouldRejectUnassignByCurrentManager() {
        MarketingCustomerInfo customer = customer(11L, "E10001", "ORG001");
        when(customerMapper.selectActiveById(11L)).thenReturn(customer);

        MarketingCustomerTransferRequest request = new MarketingCustomerTransferRequest();
        request.setTransferAction("UNASSIGN");
        request.setReason("调整");
        request.setLockVersion(1);

        assertThatThrownBy(() -> service().transfer(11L, request, "E10001", "ORG001", false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("管理员");
    }

    @Test
    void transfer_shouldRejectSelfTargetAndNonManager() {
        MarketingCustomerInfo customer = customer(12L, "E10001", "ORG001");
        when(customerMapper.selectActiveById(12L)).thenReturn(customer);

        MarketingCustomerTransferRequest self = request("TRANSFER", "E10001");
        assertThatThrownBy(() -> service().transfer(12L, self, "E10001", "ORG001", false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能包含当前主办");

        MarketingCustomerTransferRequest invalid = request("TRANSFER", "E10002");
        when(userApi.getUserRoleCodes("E10002")).thenReturn(Set.of("R_BACK_TECH"));
        assertThatThrownBy(() -> service().transfer(12L, invalid, "E10001", "ORG001", false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("角色");
    }

    @Test
    void restoreAuto_shouldFailExplicitlyUntilExternalSnapshotAdapterIsConfigured() {
        MarketingCustomerInfo customer = customer(13L, "E10001", "ORG001");
        when(customerMapper.selectActiveById(13L)).thenReturn(customer);
        MarketingCustomerOwnershipRestoreRequest request = new MarketingCustomerOwnershipRestoreRequest();
        request.setReason("恢复自动同步");
        request.setLockVersion(1);

        assertThatThrownBy(() -> service().restoreAuto(13L, request, "ADMIN", "ORG001", true))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("快照适配");
    }

    private CustomerOwnershipService service() {
        return new CustomerOwnershipService(customerMapper, transferLogMapper, transferTargetMapper, userApi);
    }

    private MarketingCustomerTransferRequest request(String action, String target) {
        MarketingCustomerTransferRequest request = new MarketingCustomerTransferRequest();
        request.setTransferAction(action);
        request.setTargetManagerId(target);
        request.setReason("调整");
        request.setLockVersion(1);
        return request;
    }

    private MarketingCustomerInfo customer(Long id, String managerId, String orgId) {
        MarketingCustomerInfo customer = new MarketingCustomerInfo();
        customer.setId(id);
        customer.setCustName("客户" + id);
        customer.setUnifiedCreditCode("913100000000000001");
        customer.setMainManagerId(managerId);
        customer.setMainOrgId(orgId);
        customer.setOwnershipStatus("ASSIGNED");
        customer.setOwnershipMaintainMode("AUTO");
        customer.setRecordStatus("ACTIVE");
        customer.setLockVersion(1);
        customer.setProfileVersion(1);
        return customer;
    }

    private UserDTO user(String empId, String orgId) {
        UserDTO user = new UserDTO();
        user.setEmpId(empId);
        user.setDisplayName(empId);
        user.setMainOrgCode(orgId);
        user.setEnabled(true);
        return user;
    }
}
