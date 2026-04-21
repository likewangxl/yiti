package com.bank.branch.platform.workflow.facade;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.workflow.api.dto.ApprovalLogDTO;
import com.bank.branch.platform.workflow.api.dto.BizProcessMapDTO;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramDTO;
import com.bank.branch.platform.workflow.api.dto.TaskDetailRespDTO;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import com.bank.branch.platform.workflow.service.ProcessQueryService;
import com.bank.branch.platform.workflow.service.ProcessStartService;
import com.bank.branch.platform.workflow.service.TodoQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorkflowQueryFacade 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowQueryFacadeTest {

    @Mock
    private TodoQueryService todoQueryService;

    @Mock
    private ProcessQueryService processQueryService;

    @Mock
    private ProcessStartService processStartService;

    @InjectMocks
    private WorkflowQueryFacade workflowQueryFacade;

    @Test
    void queryTodoList_shouldDelegateToTodoQueryService() {
        PageResult<TaskRespDTO> pageResult = PageResult.of(1, 20, 1L, List.of(new TaskRespDTO()));
        when(todoQueryService.queryTodoList("E10001", "LOAN", "审批", 1, 20)).thenReturn(pageResult);

        PageResult<TaskRespDTO> result = workflowQueryFacade.queryTodoList("E10001", "LOAN", "审批", 1, 20);

        assertThat(result).isSameAs(pageResult);
        verify(todoQueryService).queryTodoList("E10001", "LOAN", "审批", 1, 20);
    }

    @Test
    void queryDoneList_shouldDelegateToTodoQueryService() {
        PageResult<TaskRespDTO> pageResult = PageResult.of(1, 20, 1L, List.of(new TaskRespDTO()));
        when(todoQueryService.queryDoneList("E10001", "LOAN", "审批", 1, 20)).thenReturn(pageResult);

        PageResult<TaskRespDTO> result = workflowQueryFacade.queryDoneList("E10001", "LOAN", "审批", 1, 20);

        assertThat(result).isSameAs(pageResult);
        verify(todoQueryService).queryDoneList("E10001", "LOAN", "审批", 1, 20);
    }

    @Test
    void countPendingTasks_shouldReuseTodoQueryListTotal() {
        TaskRespDTO task = new TaskRespDTO();
        when(todoQueryService.queryTodoList("E10001", null, null, 1, 1))
                .thenReturn(PageResult.of(1, 1, 8L, List.of(task)));

        int count = workflowQueryFacade.countPendingTasks("E10001");

        assertThat(count).isEqualTo(8);
        verify(todoQueryService).queryTodoList("E10001", null, null, 1, 1);
    }

    @Test
    void listRecentPendingTasks_shouldReuseTodoQueryListRecords() {
        TaskRespDTO task = new TaskRespDTO();
        task.setTaskId("TASK_001");
        when(todoQueryService.queryTodoList("E10001", null, null, 1, 5))
                .thenReturn(PageResult.of(1, 5, 1L, List.of(task)));

        List<TaskRespDTO> result = workflowQueryFacade.listRecentPendingTasks("E10001", 5);

        assertThat(result).extracting(TaskRespDTO::getTaskId).containsExactly("TASK_001");
        verify(todoQueryService).queryTodoList("E10001", null, null, 1, 5);
    }

    @Test
    void listRecentPendingTasks_withNonPositiveLimit_shouldReturnEmptyList() {
        List<TaskRespDTO> result = workflowQueryFacade.listRecentPendingTasks("E10001", 0);

        assertThat(result).isEmpty();
    }

    @Test
    void getTaskDetail_shouldDelegateToTodoQueryService() {
        TaskDetailRespDTO detailRespDTO = new TaskDetailRespDTO();
        when(todoQueryService.getTaskDetail("TASK_001", "E10001")).thenReturn(detailRespDTO);

        TaskDetailRespDTO result = workflowQueryFacade.getTaskDetail("TASK_001", "E10001");

        assertThat(result).isSameAs(detailRespDTO);
        verify(todoQueryService).getTaskDetail("TASK_001", "E10001");
    }

    @Test
    void getProcessHistory_shouldDelegateToProcessQueryService() {
        List<ApprovalLogDTO> logs = List.of(new ApprovalLogDTO());
        when(processQueryService.getProcessHistory("PI_001")).thenReturn(logs);

        List<ApprovalLogDTO> result = workflowQueryFacade.getProcessHistory("PI_001");

        assertThat(result).isSameAs(logs);
        verify(processQueryService).getProcessHistory("PI_001");
    }

    @Test
    void getProcessNodes_shouldDelegateToProcessQueryService() {
        ProcessDiagramDTO dto = new ProcessDiagramDTO();
        when(processQueryService.getProcessNodes("PI_001")).thenReturn(dto);

        ProcessDiagramDTO result = workflowQueryFacade.getProcessNodes("PI_001");

        assertThat(result).isSameAs(dto);
        verify(processQueryService).getProcessNodes("PI_001");
    }

    @Test
    void getProcessByBusinessKey_shouldDelegateToProcessStartService() {
        BizProcessMapDTO dto = new BizProcessMapDTO();
        when(processStartService.getProcessByBusinessKey("LOAN:1001")).thenReturn(dto);

        BizProcessMapDTO result = workflowQueryFacade.getProcessByBusinessKey("LOAN:1001");

        assertThat(result).isSameAs(dto);
        verify(processStartService).getProcessByBusinessKey("LOAN:1001");
    }

    @Test
    void getProcessByBizTypeAndBizId_shouldDelegateToProcessStartService() {
        BizProcessMapDTO dto = new BizProcessMapDTO();
        when(processStartService.getProcessByBizTypeAndBizId("LOAN", "1001")).thenReturn(dto);

        BizProcessMapDTO result = workflowQueryFacade.getProcessByBizTypeAndBizId("LOAN", "1001");

        assertThat(result).isSameAs(dto);
        verify(processStartService).getProcessByBizTypeAndBizId("LOAN", "1001");
    }
}
