package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.PermissionDeniedException;
import com.bank.branch.platform.workflow.api.dto.CancelProcessReqDTO;
import com.bank.branch.platform.workflow.api.dto.ProcessSubmitReqDTO;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.enums.ProcessStatus;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProcessCommandService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ProcessCommandServiceTest {

    @Mock
    private ProcessStartService processStartService;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private BizProcessMapMapper bizProcessMapMapper;

    @Mock
    private CurrentUserApi currentUserApi;

    @InjectMocks
    private ProcessCommandService processCommandService;

    @Test
    void submitProcess_shouldUseCurrentUserAndDelegateStart() {
        ProcessSubmitReqDTO req = new ProcessSubmitReqDTO();
        req.setBizType("LOAN");
        req.setBizId("LA20260414001");
        req.setBusinessKey("LOAN:LA20260414001");
        req.setProcessDefinitionKey("loan_approve_v1");
        req.setTitle("资产投放申请-提交流程");
        req.setVariables(Map.of("amount", 1000));

        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("BJ_CY");

        WorkflowLaunchResp expected = new WorkflowLaunchResp("PID_001", "LOAN:LA20260414001", "TASK_001");
        when(processStartService.startProcess(org.mockito.ArgumentMatchers.any(StartProcessCmd.class)))
                .thenReturn(expected);

        WorkflowLaunchResp resp = processCommandService.submitProcess(req);

        assertThat(resp.getProcessInstanceId()).isEqualTo("PID_001");

        ArgumentCaptor<StartProcessCmd> captor = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(processStartService).startProcess(captor.capture());
        StartProcessCmd actual = captor.getValue();
        assertThat(actual.getStartUser()).isEqualTo("E10001");
        assertThat(actual.getStartOrgId()).isEqualTo("BJ_CY");
        assertThat(actual.getBusinessKey()).isEqualTo("LOAN:LA20260414001");
        assertThat(actual.getVariables()).containsEntry("amount", 1000);
        assertThat(actual.getVariables()).containsEntry("startUser", "E10001");
        assertThat(actual.getVariables()).containsEntry("startOrgId", "BJ_CY");
    }

    @Test
    void cancelProcess_notStarter_throwsPermissionDenied() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E30001");
        when(currentUserApi.isSystemAdmin()).thenReturn(false);

        BizProcessMap map = new BizProcessMap();
        map.setProcessInstanceId("PID_001");
        map.setStartUser("E10001");
        map.setProcessStatus(ProcessStatus.RUNNING.getCode());
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_001")).thenReturn(map);

        CancelProcessReqDTO req = new CancelProcessReqDTO();
        req.setReason("发起人撤回");

        assertThatThrownBy(() -> processCommandService.cancelProcess("PID_001", req))
                .isInstanceOf(PermissionDeniedException.class)
                .extracting("code")
                .isEqualTo("AUTH-40305");
    }

    @Test
    void cancelProcess_processNotRunning_throwsWf40905() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.isSystemAdmin()).thenReturn(false);

        BizProcessMap map = new BizProcessMap();
        map.setProcessInstanceId("PID_001");
        map.setStartUser("E10001");
        map.setProcessStatus(ProcessStatus.RUNNING.getCode());
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_001")).thenReturn(map);

        ProcessInstanceQuery query = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
        when(query.processInstanceId("PID_001")).thenReturn(query);
        when(query.singleResult()).thenReturn(null);

        CancelProcessReqDTO req = new CancelProcessReqDTO();
        req.setReason("发起人撤回");

        assertThatThrownBy(() -> processCommandService.cancelProcess("PID_001", req))
                .extracting("code")
                .isEqualTo("WF-40905");
    }

    @Test
    void cancelProcess_success_deletesProcessAndUpdatesMap() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.isSystemAdmin()).thenReturn(false);

        BizProcessMap map = new BizProcessMap();
        map.setId("MAP_001");
        map.setProcessInstanceId("PID_001");
        map.setStartUser("E10001");
        map.setProcessStatus(ProcessStatus.RUNNING.getCode());
        map.setCurrentAssignee("E20001");
        when(bizProcessMapMapper.selectByProcessInstanceId("PID_001")).thenReturn(map);

        ProcessInstance runtimeInstance = mock(ProcessInstance.class);
        ProcessInstanceQuery query = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
        when(query.processInstanceId("PID_001")).thenReturn(query);
        when(query.singleResult()).thenReturn(runtimeInstance);

        CancelProcessReqDTO req = new CancelProcessReqDTO();
        req.setReason("发起人撤回");

        processCommandService.cancelProcess("PID_001", req);

        verify(runtimeService).deleteProcessInstance("PID_001", "发起人撤回");
        verify(bizProcessMapMapper).updateById(org.mockito.ArgumentMatchers.argThat(updated ->
                ProcessStatus.CANCELLED.getCode().equals(updated.getProcessStatus())
                        && updated.getEndTime() != null
                        && updated.getCurrentAssignee() == null
        ));
    }
}
