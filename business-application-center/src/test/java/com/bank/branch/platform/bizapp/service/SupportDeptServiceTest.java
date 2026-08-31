package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.enums.SupportStatus;
import com.bank.branch.platform.bizapp.event.SupportCompletedEvent;
import com.bank.branch.platform.bizapp.event.SupportDispatchedEvent;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SupportDeptService 单元测试（TDD）。
 * 共 11 个测试用例。
 */
@ExtendWith(MockitoExtension.class)
class SupportDeptServiceTest {

    @Mock
    private SupportRequestMapper supportMapper;

    @Mock
    private BizStateMachine bizStateMachine;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SupportDeptService supportDeptService;

    // ==================== dispatch ====================

    @Test
    void dispatch_inApproval_shouldTransitionToInProgress() {
        // given
        SupportRequest inApproval = buildRequest("SR001", SupportStatus.IN_APPROVAL);
        inApproval.setSupportDeptId("DEPT001");
        when(supportMapper.selectForUpdate("SR001")).thenReturn(inApproval);
        doNothing().when(bizStateMachine).validateSupportTransition(anyString(), anyString());
        when(supportMapper.updateById(any(SupportRequest.class))).thenReturn(1);

        // when
        supportDeptService.dispatch("SR001", "E20001", "E10001", null);

        // then
        ArgumentCaptor<SupportRequest> captor = ArgumentCaptor.forClass(SupportRequest.class);
        verify(supportMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SupportStatus.IN_PROGRESS.getCode());
    }

    @Test
    void dispatch_notInApproval_shouldThrow() {
        // given: 已经是 IN_PROGRESS
        SupportRequest inProgress = buildRequest("SR001", SupportStatus.IN_PROGRESS);
        when(supportMapper.selectForUpdate("SR001")).thenReturn(inProgress);
        org.mockito.Mockito.doThrow(new BizException("BIZ-42301", "非法状态迁移"))
                .when(bizStateMachine).validateSupportTransition(
                        eq(SupportStatus.IN_PROGRESS.getCode()),
                        eq(SupportStatus.IN_PROGRESS.getCode())
                );

        assertThatThrownBy(() -> supportDeptService.dispatch("SR001", "E20001", "E10001", null))
                .isInstanceOf(BizException.class);
    }

    @Test
    void dispatch_shouldSetDispatchFields() {
        // given
        SupportRequest inApproval = buildRequest("SR001", SupportStatus.IN_APPROVAL);
        inApproval.setSupportDeptId("DEPT001");
        when(supportMapper.selectForUpdate("SR001")).thenReturn(inApproval);
        doNothing().when(bizStateMachine).validateSupportTransition(anyString(), anyString());
        when(supportMapper.updateById(any(SupportRequest.class))).thenReturn(1);

        // when
        supportDeptService.dispatch("SR001", "E20001", "E10001", null);

        // then: 应设置 dispatch 相关字段
        ArgumentCaptor<SupportRequest> captor = ArgumentCaptor.forClass(SupportRequest.class);
        verify(supportMapper).updateById(captor.capture());
        assertThat(captor.getValue().getAssignedEmpId()).isEqualTo("E20001");
        assertThat(captor.getValue().getDispatchEmpId()).isEqualTo("E10001");
        assertThat(captor.getValue().getDispatchTime()).isNotNull();
        verify(eventPublisher).publishEvent(any(SupportDispatchedEvent.class));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("dispatch Service 接收 dispatchRemark 并写入 SupportDispatchedEvent")
    void dispatch_publishesEventWithDispatchRemark() {
        // given
        SupportRequest inApproval = buildRequest("SR001", SupportStatus.IN_APPROVAL);
        inApproval.setSupportDeptId("DEPT001");
        when(supportMapper.selectForUpdate("SR001")).thenReturn(inApproval);
        doNothing().when(bizStateMachine).validateSupportTransition(anyString(), anyString());
        when(supportMapper.updateById(any(SupportRequest.class))).thenReturn(1);

        // when
        supportDeptService.dispatch("SR001", "E20001", "E10001", "请优先处理");

        // then: 事件载荷中包含 dispatchRemark
        ArgumentCaptor<SupportDispatchedEvent> captor = ArgumentCaptor.forClass(SupportDispatchedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getDispatchRemark()).isEqualTo("请优先处理");
    }

    // ==================== transfer ====================

    @Test
    void transfer_validInProgress_shouldUpdateAssignee() {
        // given
        SupportRequest inProgress = buildRequest("SR001", SupportStatus.IN_PROGRESS);
        inProgress.setAssignedEmpId("E20001");
        when(supportMapper.selectForUpdate("SR001")).thenReturn(inProgress);
        when(supportMapper.updateById(any(SupportRequest.class))).thenReturn(1);

        // when: 当前承接人 E20001 转交给 E30001
        supportDeptService.transfer("SR001", "E30001", "E20001");

        // then
        ArgumentCaptor<SupportRequest> captor = ArgumentCaptor.forClass(SupportRequest.class);
        verify(supportMapper).updateById(captor.capture());
        assertThat(captor.getValue().getAssignedEmpId()).isEqualTo("E30001");
    }

    @Test
    void transfer_notAssignedPerson_shouldThrowBIZ40304() {
        // given: assignedEmpId=E20001，但操作人是 E99999
        SupportRequest inProgress = buildRequest("SR001", SupportStatus.IN_PROGRESS);
        inProgress.setAssignedEmpId("E20001");
        when(supportMapper.selectForUpdate("SR001")).thenReturn(inProgress);

        // when / then
        assertThatThrownBy(() -> supportDeptService.transfer("SR001", "E30001", "E99999"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40304");
    }

    // ==================== complete ====================

    @Test
    void complete_scenarioA_success_shouldComplete() {
        // given: 场景A，IN_APPROVAL -> COMPLETED（无 dispatchEmpId）
        SupportRequest inApproval = buildRequest("SR001", SupportStatus.IN_APPROVAL);
        inApproval.setProductId("P001");
        when(supportMapper.selectForUpdate("SR001")).thenReturn(inApproval);
        doNothing().when(bizStateMachine).validateSupportTransition(anyString(), anyString());
        when(supportMapper.updateById(any(SupportRequest.class))).thenReturn(1);

        // when
        supportDeptService.complete("SR001", true, "E20001");

        // then
        ArgumentCaptor<SupportRequest> captor = ArgumentCaptor.forClass(SupportRequest.class);
        verify(supportMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SupportStatus.COMPLETED.getCode());
    }

    @Test
    void complete_scenarioB_inProgress_success_shouldComplete() {
        // given: 场景B，IN_PROGRESS -> COMPLETED
        SupportRequest inProgress = buildRequest("SR001", SupportStatus.IN_PROGRESS);
        inProgress.setSupportDeptId("DEPT001");
        when(supportMapper.selectForUpdate("SR001")).thenReturn(inProgress);
        doNothing().when(bizStateMachine).validateSupportTransition(anyString(), anyString());
        when(supportMapper.updateById(any(SupportRequest.class))).thenReturn(1);

        // when
        supportDeptService.complete("SR001", true, "E20001");

        // then
        ArgumentCaptor<SupportRequest> captor = ArgumentCaptor.forClass(SupportRequest.class);
        verify(supportMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SupportStatus.COMPLETED.getCode());
    }

    @Test
    void complete_scenarioB_inProgress_failed_shouldReject() {
        // given: 场景B，IN_PROGRESS -> REJECTED（success=false）
        SupportRequest inProgress = buildRequest("SR001", SupportStatus.IN_PROGRESS);
        inProgress.setSupportDeptId("DEPT001");
        when(supportMapper.selectForUpdate("SR001")).thenReturn(inProgress);
        doNothing().when(bizStateMachine).validateSupportTransition(anyString(), anyString());
        when(supportMapper.updateById(any(SupportRequest.class))).thenReturn(1);

        // when
        supportDeptService.complete("SR001", false, "E20001");

        // then
        ArgumentCaptor<SupportRequest> captor = ArgumentCaptor.forClass(SupportRequest.class);
        verify(supportMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SupportStatus.REJECTED.getCode());
    }

    @Test
    void complete_invalidStatus_shouldThrow() {
        // given: DRAFT 状态不允许直接 complete
        SupportRequest draft = buildRequest("SR001", SupportStatus.DRAFT);
        when(supportMapper.selectForUpdate("SR001")).thenReturn(draft);
        org.mockito.Mockito.doThrow(new BizException("BIZ-42301", "非法状态迁移"))
                .when(bizStateMachine).validateSupportTransition(
                        eq(SupportStatus.DRAFT.getCode()), anyString()
                );

        assertThatThrownBy(() -> supportDeptService.complete("SR001", true, "E20001"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void complete_shouldPublishSupportCompletedEvent() {
        // given
        SupportRequest inProgress = buildRequest("SR001", SupportStatus.IN_PROGRESS);
        inProgress.setSupportDeptId("DEPT001");
        inProgress.setAssignedEmpId("E20001");
        when(supportMapper.selectForUpdate("SR001")).thenReturn(inProgress);
        doNothing().when(bizStateMachine).validateSupportTransition(anyString(), anyString());
        when(supportMapper.updateById(any(SupportRequest.class))).thenReturn(1);

        // when
        supportDeptService.complete("SR001", true, "E20001");

        // then
        verify(eventPublisher).publishEvent(any(SupportCompletedEvent.class));
    }

    // ==================== listPageForDept ====================

    @Test
    void listPageForDept_shouldReturnPageResult() {
        // given
        SupportRequest sr = buildRequest("SR001", SupportStatus.IN_PROGRESS);
        when(supportMapper.selectPageForDept(anyString(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(sr));
        when(supportMapper.countPageForDept(anyString(), any(), any())).thenReturn(1L);

        // when
        PageResult<SupportRequest> result = supportDeptService.listPageForDept("DEPT001", null, null, 1, 20);

        // then
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
    }

    // ==================== 辅助方法 ====================

    private SupportRequest buildRequest(String id, SupportStatus status) {
        SupportRequest sr = new SupportRequest();
        sr.setId(id);
        sr.setRequestNo("SR20260414000001");
        sr.setCustId("CUST001");
        sr.setStatus(status.getCode());
        sr.setBusinessKey("SUPPORT:" + id);
        sr.setOwnerOrgId("ORG001");
        sr.setDeleted(0);
        return sr;
    }
}
