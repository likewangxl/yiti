package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.customer.mapper.CustTransferLogMapper;
import com.bank.branch.platform.customer.mapper.CustTransferTargetMapper;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerTransferServiceTest {

    @Mock CustMasterMapper masterMapper;
    @Mock CustTransferLogMapper transferLogMapper;
    @Mock CustTransferTargetMapper transferTargetMapper;
    @Mock TouchTaskMapper touchTaskMapper;
    @Mock TouchTaskService touchTaskService;
    @Mock UserApi userApi;

    @Test
    void transfer_shouldUpdateMainManagerCancelOldTasksPersistTargetsAndCreateNewTask() {
        CustomerTransferService service = new CustomerTransferService(masterMapper, transferLogMapper,
                transferTargetMapper, touchTaskMapper, touchTaskService, userApi);
        CustMaster customer = new CustMaster();
        customer.setId("C001");
        customer.setCustName("测试客户");
        customer.setMainManagerId("OLD");
        customer.setMainOrgId("ORG-OLD");
        customer.setIsAccountOpened(1);
        when(masterMapper.selectById("C001")).thenReturn(customer);
        when(userApi.getUserRoleCodes("NEW1")).thenReturn(Set.of("CUST_MARKETING_MANAGER"));
        when(userApi.getUserRoleCodes("NEW2")).thenReturn(Set.of("CUST_MARKETING_MANAGER"));
        when(userApi.getUserByEmpId("NEW1")).thenReturn(user("NEW1", "ORG-NEW"));
        when(userApi.getUserByEmpId("NEW2")).thenReturn(user("NEW2", "ORG-NEW"));

        service.transfer("C001", List.of("NEW1", "NEW2"), "岗位调整", "ADMIN");

        verify(touchTaskMapper).cancelActiveByCust(any(), any());
        verify(transferTargetMapper).insertBatch(any());
        verify(touchTaskService).createFirstTouchTask("C001", "ORG-NEW", "NEW1", null);
        ArgumentCaptor<CustMaster> captor = ArgumentCaptor.forClass(CustMaster.class);
        verify(masterMapper).updateById(captor.capture());
        assertThat(captor.getValue().getMainManagerId()).isEqualTo("NEW1");
        assertThat(captor.getValue().getMainOrgId()).isEqualTo("ORG-NEW");
    }

    @Test
    void transfer_shouldRejectCustomerOutsideOperatorMainOrg() {
        CustomerTransferService service = new CustomerTransferService(masterMapper, transferLogMapper,
                transferTargetMapper, touchTaskMapper, touchTaskService, userApi);
        CustMaster customer = new CustMaster();
        customer.setId("C001");
        customer.setMainManagerId("OLD");
        customer.setMainOrgId("ORG-OLD");
        when(masterMapper.selectById("C001")).thenReturn(customer);

        assertThatThrownBy(() -> service.transfer("C001", List.of("NEW1"), "岗位调整",
                "OTHER", "ORG-OTHER", false))
                .isInstanceOf(com.bank.branch.platform.common.web.exception.BizException.class)
                .hasMessageContaining("无权转交");
    }

    private UserDTO user(String empId, String orgCode) {
        UserDTO user = new UserDTO();
        user.setEmpId(empId);
        user.setDisplayName(empId);
        user.setMainOrgCode(orgCode);
        user.setMainOrgName(orgCode);
        user.setEnabled(true);
        return user;
    }
}
