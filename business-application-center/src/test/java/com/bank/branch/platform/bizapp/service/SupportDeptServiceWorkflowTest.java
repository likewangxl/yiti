package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.bizapp.dto.req.CompleteReq;
import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.enums.SupportStatus;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import com.bank.branch.platform.bizapp.api.converter.SupportRequestDTOConverter;
import com.bank.branch.platform.workflow.api.TodoQueryApi;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 工作流驱动的承接完成路径回归测试。 */
class SupportDeptServiceWorkflowTest {

    @Test
    void complete_scenarioA_requiresProcessLogAndDelegatesTerminalStateToListener() {
        SupportRequestMapper mapper = mock(SupportRequestMapper.class);
        BizStateMachine stateMachine = mock(BizStateMachine.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        SupportRequestDTOConverter converter = mock(SupportRequestDTOConverter.class);
        TodoQueryApi todoQueryApi = mock(TodoQueryApi.class);
        WorkflowApi workflowApi = mock(WorkflowApi.class);
        SupportProcessLogService processLogService = mock(SupportProcessLogService.class);

        SupportRequest request = new SupportRequest();
        request.setId("SR-A-001");
        request.setRequestNo("SR20260901000002");
        request.setCustId("CUST001");
        request.setProductId("P001");
        request.setSupportDeptId(null);
        request.setAssignedEmpId("E10001");
        request.setBusinessKey("SUPPORT:SR-A-001");
        request.setStatus(SupportStatus.IN_APPROVAL.getCode());
        when(mapper.selectForUpdate("SR-A-001")).thenReturn(request);
        doNothing().when(stateMachine).validateSupportTransition(anyString(), anyString());
        when(processLogService.countProcess("SR-A-001")).thenReturn(1L);

        TaskRespDTO task = new TaskRespDTO();
        task.setTaskId("TASK-A-001");
        task.setBusinessKey("SUPPORT:SR-A-001");
        task.setBizType("SUPPORT");
        task.setNodeKey("product_owner_handle");
        when(todoQueryApi.listMyTodoBusinessKeys("E10001", "SUPPORT"))
                .thenReturn(List.of("SUPPORT:SR-A-001"));
        when(todoQueryApi.findTaskRespByBusinessKeys("E10001", List.of("SUPPORT:SR-A-001")))
                .thenReturn(Map.of("SUPPORT:SR-A-001", task));

        CompleteReq req = new CompleteReq();
        req.setSuccess(true);
        req.setHandleResult("产品支持已完成");

        SupportDeptService service = new SupportDeptService(
                mapper, stateMachine, publisher, converter, todoQueryApi, workflowApi, null,
                processLogService);

        service.complete("SR-A-001", req, "E10001");

        verify(processLogService).countProcess("SR-A-001");
        verify(processLogService).appendResultLog("SR-A-001", "产品支持已完成", "E10001", List.of());
        verify(workflowApi).approveByEmp(eq("TASK-A-001"), eq("E10001"), eq("产品支持已完成"), any(Map.class));
        verify(mapper, never()).updateById(any(SupportRequest.class));
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void complete_scenarioA_withoutProcessLog_isRejectedBeforeWorkflowCompletion() {
        SupportRequestMapper mapper = mock(SupportRequestMapper.class);
        BizStateMachine stateMachine = mock(BizStateMachine.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        SupportRequestDTOConverter converter = mock(SupportRequestDTOConverter.class);
        TodoQueryApi todoQueryApi = mock(TodoQueryApi.class);
        WorkflowApi workflowApi = mock(WorkflowApi.class);
        SupportProcessLogService processLogService = mock(SupportProcessLogService.class);

        SupportRequest request = new SupportRequest();
        request.setId("SR-A-002");
        request.setProductId("P001");
        request.setAssignedEmpId("E10001");
        request.setBusinessKey("SUPPORT:SR-A-002");
        request.setStatus(SupportStatus.IN_APPROVAL.getCode());
        when(mapper.selectForUpdate("SR-A-002")).thenReturn(request);
        when(processLogService.countProcess("SR-A-002")).thenReturn(0L);

        TaskRespDTO task = new TaskRespDTO();
        task.setTaskId("TASK-A-002");
        task.setBusinessKey("SUPPORT:SR-A-002");
        task.setBizType("SUPPORT");
        task.setNodeKey("product_owner_handle");
        when(todoQueryApi.listMyTodoBusinessKeys("E10001", "SUPPORT"))
                .thenReturn(List.of("SUPPORT:SR-A-002"));
        when(todoQueryApi.findTaskRespByBusinessKeys("E10001", List.of("SUPPORT:SR-A-002")))
                .thenReturn(Map.of("SUPPORT:SR-A-002", task));

        CompleteReq req = new CompleteReq();
        req.setSuccess(true);
        req.setHandleResult("结果");

        SupportDeptService service = new SupportDeptService(
                mapper, stateMachine, publisher, converter, todoQueryApi, workflowApi, null,
                processLogService);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.complete("SR-A-002", req, "E10001"))
                .isInstanceOf(com.bank.branch.platform.common.web.exception.BizException.class)
                .hasMessageContaining("过程记录");
        verify(workflowApi, never()).approveByEmp(anyString(), anyString(), anyString(), any(Map.class));
        verify(workflowApi, never()).rejectByEmp(anyString(), anyString(), anyString());
    }

    @Test
    void complete_withCurrentWorkflowTask_shouldDelegateTerminalStateToListener() {
        SupportRequestMapper mapper = mock(SupportRequestMapper.class);
        BizStateMachine stateMachine = mock(BizStateMachine.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        SupportRequestDTOConverter converter = mock(SupportRequestDTOConverter.class);
        TodoQueryApi todoQueryApi = mock(TodoQueryApi.class);
        WorkflowApi workflowApi = mock(WorkflowApi.class);

        SupportRequest request = new SupportRequest();
        request.setId("SR-WF-001");
        request.setRequestNo("SR20260901000001");
        request.setCustId("CUST001");
        request.setSupportDeptId("DEPT001");
        request.setAssignedEmpId("E20001");
        request.setBusinessKey("SUPPORT:SR-WF-001");
        request.setStatus(SupportStatus.IN_PROGRESS.getCode());
        when(mapper.selectForUpdate("SR-WF-001")).thenReturn(request);
        doNothing().when(stateMachine).validateSupportTransition(anyString(), anyString());

        TaskRespDTO task = new TaskRespDTO();
        task.setTaskId("TASK-001");
        task.setBusinessKey("SUPPORT:SR-WF-001");
        task.setBizType("SUPPORT");
        task.setNodeKey("support_staff_handle");
        when(todoQueryApi.listMyTodoBusinessKeys("E20001", "SUPPORT"))
                .thenReturn(List.of("SUPPORT:SR-WF-001"));
        when(todoQueryApi.findTaskRespByBusinessKeys("E20001", List.of("SUPPORT:SR-WF-001")))
                .thenReturn(Map.of("SUPPORT:SR-WF-001", task));

        CompleteReq req = new CompleteReq();
        req.setSuccess(true);
        req.setHandleResult("已完成支持");

        SupportDeptService service = new SupportDeptService(
                mapper, stateMachine, publisher, converter, todoQueryApi, workflowApi, null, null);

        service.complete("SR-WF-001", req, "E20001");

        verify(workflowApi).approveByEmp(eq("TASK-001"), eq("E20001"), eq("已完成支持"), any(Map.class));
        verify(mapper, never()).updateById(any(SupportRequest.class));
        verify(publisher, never()).publishEvent(any());
    }
}
