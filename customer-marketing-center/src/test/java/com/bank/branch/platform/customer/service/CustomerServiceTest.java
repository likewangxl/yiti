package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.CustClaim;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.enums.ClaimStatus;
import com.bank.branch.platform.customer.enums.CustMasterStatus;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.LeadOp;
import com.bank.branch.platform.customer.event.ClaimTransferredEvent;
import com.bank.branch.platform.customer.mapper.CustClaimMapper;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CustomerService 单元测试（TDD Red 阶段）
 * 覆盖：getById / listPage / transfer / deleteApply 的核心场景
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustMasterMapper masterMapper;

    @Mock
    private CustClaimMapper claimMapper;

    @Mock
    private CustLeadMapper leadMapper;

    @Mock
    private WorkflowApi workflowApi;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CustomerService customerService;

    // ==================== getById ====================

    @Test
    void getById_shouldReturnCustomer() {
        // given
        CustMaster master = new CustMaster();
        master.setId("cust-001");
        master.setCustName("测试客户");
        master.setStatus(CustMasterStatus.ACTIVE.getCode());
        when(masterMapper.selectById("cust-001")).thenReturn(master);

        // when
        CustMaster result = customerService.getById("cust-001");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("cust-001");
        assertThat(result.getCustName()).isEqualTo("测试客户");
    }

    @Test
    void getById_shouldThrowWhenNotFound() {
        // given
        when(masterMapper.selectById("not-exist")).thenReturn(null);

        // when/then
        assertThatThrownBy(() -> customerService.getById("not-exist"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.CUSTOMER_NOT_FOUND.getCode());
    }

    // ==================== listPage ====================

    @Test
    void listPage_shouldCalculateOffset() {
        // given: pageNo=2, pageSize=10 -> offset = (2-1)*10 = 10
        CustMaster master = new CustMaster();
        master.setId("cust-001");
        when(masterMapper.selectPage(isNull(), isNull(), eq(10), eq(10)))
                .thenReturn(Collections.singletonList(master));
        when(masterMapper.countPage(isNull(), isNull())).thenReturn(25L);

        // when
        PageResult<CustMaster> result = customerService.listPage(null, null, 2, 10);

        // then
        assertThat(result.getPageNo()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getTotal()).isEqualTo(25L);
        assertThat(result.getRecords()).hasSize(1);
        verify(masterMapper).selectPage(null, null, 10, 10);
    }

    // ==================== transfer ====================

    @Test
    void transfer_shouldUpdateMaintainer() {
        // given: 存在有效认领记录
        String claimId = "claim-001";
        String custId = "cust-001";
        String fromEmpId = "E10001";
        String toEmpId = "E10002";
        String operatorEmpId = "E10001";
        String reason = "员工离职交接";

        CustClaim claim = new CustClaim();
        claim.setId(claimId);
        claim.setCustId(custId);
        claim.setMaintainerEmpId(fromEmpId);
        claim.setClaimStatus(ClaimStatus.CLAIMED.getCode());
        when(claimMapper.selectById(claimId)).thenReturn(claim);
        when(claimMapper.updateById(any(CustClaim.class))).thenReturn(1);

        // when
        customerService.transfer(claimId, toEmpId, reason, operatorEmpId);

        // then: 更新 maintainerEmpId
        ArgumentCaptor<CustClaim> claimCaptor = ArgumentCaptor.forClass(CustClaim.class);
        verify(claimMapper).updateById(claimCaptor.capture());
        CustClaim updated = claimCaptor.getValue();
        assertThat(updated.getId()).isEqualTo(claimId);
        assertThat(updated.getMaintainerEmpId()).isEqualTo(toEmpId);

        // 发布 ClaimTransferredEvent
        ArgumentCaptor<ClaimTransferredEvent> eventCaptor = ArgumentCaptor.forClass(ClaimTransferredEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ClaimTransferredEvent event = eventCaptor.getValue();
        assertThat(event.getClaimId()).isEqualTo(claimId);
        assertThat(event.getCustId()).isEqualTo(custId);
        assertThat(event.getFromEmpId()).isEqualTo(fromEmpId);
        assertThat(event.getToEmpId()).isEqualTo(toEmpId);
        assertThat(event.getOperatorEmpId()).isEqualTo(operatorEmpId);
    }

    @Test
    void transfer_shouldThrowWhenReasonBlank() {
        // given: reason 为空
        String claimId = "claim-001";

        CustClaim claim = new CustClaim();
        claim.setId(claimId);
        claim.setCustId("cust-001");
        claim.setMaintainerEmpId("E10001");
        claim.setClaimStatus(ClaimStatus.CLAIMED.getCode());
        when(claimMapper.selectById(claimId)).thenReturn(claim);

        // when/then: 空 reason 抛出 TRANSFER_REASON_REQUIRED
        assertThatThrownBy(() -> customerService.transfer(claimId, "E10002", "", "E10001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TRANSFER_REASON_REQUIRED.getCode());

        verify(claimMapper, never()).updateById(any());
    }

    @Test
    void transfer_shouldThrowWhenClaimNotFound() {
        // given: 认领记录不存在
        when(claimMapper.selectById("not-exist")).thenReturn(null);

        // when/then
        assertThatThrownBy(() -> customerService.transfer("not-exist", "E10002", "离职交接", "E10001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.CLAIM_NOT_FOUND.getCode());

        verify(claimMapper, never()).updateById(any());
    }

    // ==================== deleteApply ====================

    @Test
    void deleteApply_shouldStartWorkflow() {
        // given: 存在客户
        String custId = "cust-001";
        String operatorEmpId = "E10001";
        String orgCode = "ORG_SZ_001";

        CustMaster master = new CustMaster();
        master.setId(custId);
        master.setCustNo("CUST_00001");
        master.setCustName("待删除客户");
        when(masterMapper.selectById(custId)).thenReturn(master);
        when(leadMapper.insert(any(CustLead.class))).thenReturn(1);

        WorkflowLaunchResp launchResp = new WorkflowLaunchResp();
        launchResp.setProcessInstanceId("proc-001");
        launchResp.setBusinessKey("LEAD:lead-xxx");
        when(workflowApi.startProcess(any(StartProcessCmd.class))).thenReturn(launchResp);

        // when
        customerService.deleteApply(custId, operatorEmpId, orgCode);

        // then: 创建了 DELETE 类型线索
        ArgumentCaptor<CustLead> leadCaptor = ArgumentCaptor.forClass(CustLead.class);
        verify(leadMapper).insert(leadCaptor.capture());
        CustLead createdLead = leadCaptor.getValue();
        assertThat(createdLead.getLeadOp()).isEqualTo(LeadOp.DELETE.getCode());
        assertThat(createdLead.getSourceCustId()).isEqualTo(custId);
        assertThat(createdLead.getCustName()).isEqualTo(master.getCustName());
        assertThat(createdLead.getCreatedBy()).isEqualTo(operatorEmpId);
        assertThat(createdLead.getOwnerOrgId()).isEqualTo(orgCode);

        // 启动了工作流
        ArgumentCaptor<StartProcessCmd> cmdCaptor = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(workflowApi).startProcess(cmdCaptor.capture());
        StartProcessCmd cmd = cmdCaptor.getValue();
        assertThat(cmd.getBizType()).isEqualTo("LEAD");
        assertThat(cmd.getBizId()).isEqualTo(createdLead.getId());
        assertThat(cmd.getStartUser()).isEqualTo(operatorEmpId);
        assertThat(cmd.getStartOrgId()).isEqualTo(orgCode);
    }

    @Test
    void deleteApply_shouldThrowWhenCustomerNotFound() {
        // given: 客户不存在
        when(masterMapper.selectById("not-exist")).thenReturn(null);

        // when/then
        assertThatThrownBy(() -> customerService.deleteApply("not-exist", "E10001", "ORG_SZ_001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.CUSTOMER_NOT_FOUND.getCode());

        verify(leadMapper, never()).insert(any());
        verify(workflowApi, never()).startProcess(any());
    }
}
