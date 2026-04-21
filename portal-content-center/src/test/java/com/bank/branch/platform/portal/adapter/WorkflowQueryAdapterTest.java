package com.bank.branch.platform.portal.adapter;

import com.bank.branch.platform.portal.adapter.dto.PortalTodoItem;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorkflowQueryAdapter 单元测试 —— 验证降级逻辑、委托行为与 DTO 映射。
 */
class WorkflowQueryAdapterTest {

    @Test
    void shouldReturnZeroAndEmptyWhenWorkflowQueryApiBeanIsNull() {
        WorkflowQueryAdapter adapter = new WorkflowQueryAdapter();

        assertThat(adapter.countPending("E10001")).isZero();
        assertThat(adapter.listPending("E10001", 5)).isEmpty();
    }

    @Test
    void shouldDelegateToWorkflowQueryApiAndConvertTaskRespDto() {
        WorkflowQueryAdapter adapter = new WorkflowQueryAdapter();
        WorkflowQueryApi mockApi = mock(WorkflowQueryApi.class);
        adapter.setWorkflowQueryApi(mockApi);

        TaskRespDTO taskRespDTO = new TaskRespDTO();
        taskRespDTO.setTaskId("T001");
        taskRespDTO.setProcessInstanceId("PI001");
        taskRespDTO.setTitle("资产投放审批");
        taskRespDTO.setTaskName("二级审批");
        taskRespDTO.setStartUserName("张三");
        taskRespDTO.setStartTime(LocalDateTime.of(2026, 4, 16, 9, 30));
        taskRespDTO.setSlaStatus("YELLOW");

        when(mockApi.countPendingTasks("E10001")).thenReturn(12);
        when(mockApi.listRecentPendingTasks("E10001", 5)).thenReturn(List.of(taskRespDTO));

        assertThat(adapter.countPending("E10001")).isEqualTo(12);

        List<PortalTodoItem> result = adapter.listPending("E10001", 5);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTaskId()).isEqualTo("T001");
        assertThat(result.get(0).getProcessInstanceId()).isEqualTo("PI001");
        assertThat(result.get(0).getProcessName()).isEqualTo("资产投放审批");
        assertThat(result.get(0).getTaskTitle()).isEqualTo("二级审批");
        assertThat(result.get(0).getInitiatorName()).isEqualTo("张三");
        assertThat(result.get(0).getInitiatedTime()).isEqualTo("2026-04-16T09:30");
        assertThat(result.get(0).getLightStatus()).isEqualTo("YELLOW");
        assertThat(result.get(0).getOverdueInfo()).isNull();
        assertThat(result.get(0).getBizDetailUrl()).isNull();

        verify(mockApi).countPendingTasks("E10001");
        verify(mockApi).listRecentPendingTasks("E10001", 5);
    }

    @Test
    void shouldFallbackWhenWorkflowQueryApiThrowsException() {
        WorkflowQueryAdapter adapter = new WorkflowQueryAdapter();
        WorkflowQueryApi mockApi = mock(WorkflowQueryApi.class);
        adapter.setWorkflowQueryApi(mockApi);

        when(mockApi.countPendingTasks("E10001"))
                .thenThrow(new RuntimeException("Flowable connection failed"));
        when(mockApi.listRecentPendingTasks("E10001", 5))
                .thenThrow(new RuntimeException("Flowable connection failed"));

        assertThat(adapter.countPending("E10001")).isZero();
        assertThat(adapter.listPending("E10001", 5)).isEmpty();
    }

    @Test
    void shouldFallbackToStartUserAndTaskCreateTimeWhenReadableFieldsMissing() {
        WorkflowQueryAdapter adapter = new WorkflowQueryAdapter();
        WorkflowQueryApi mockApi = mock(WorkflowQueryApi.class);
        adapter.setWorkflowQueryApi(mockApi);

        TaskRespDTO taskRespDTO = new TaskRespDTO();
        taskRespDTO.setTaskId("T002");
        taskRespDTO.setTitle("流程标题");
        taskRespDTO.setStartUser("E10002");
        taskRespDTO.setTaskCreateTime(LocalDateTime.of(2026, 4, 16, 10, 15));

        when(mockApi.listRecentPendingTasks("E10001", 1)).thenReturn(List.of(taskRespDTO));

        List<PortalTodoItem> result = adapter.listPending("E10001", 1);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.getProcessName()).isEqualTo("流程标题");
            assertThat(item.getTaskTitle()).isEqualTo("流程标题");
            assertThat(item.getInitiatorName()).isEqualTo("E10002");
            assertThat(item.getInitiatedTime()).isEqualTo("2026-04-16T10:15");
        });
    }
}
