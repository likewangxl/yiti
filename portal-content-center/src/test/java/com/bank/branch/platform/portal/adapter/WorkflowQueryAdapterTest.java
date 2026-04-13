package com.bank.branch.platform.portal.adapter;

import com.bank.branch.platform.portal.adapter.dto.PortalTodoItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * WorkflowQueryAdapter 单元测试 —— 验证降级逻辑与委托行为。
 */
class WorkflowQueryAdapterTest {

    /**
     * 当 WorkflowQueryApi bean 为 null（V1 阶段无实现）时，
     * countPending 应返回 0，listPending 应返回空列表。
     */
    @Test
    void shouldReturnZeroAndEmptyWhenWorkflowQueryApiBeanIsNull() {
        WorkflowQueryAdapter adapter = new WorkflowQueryAdapter();
        // 不设置 workflowQueryApi，保持 null

        assertThat(adapter.countPending("E10001")).isZero();
        assertThat(adapter.listPending("E10001", 5)).isEmpty();
    }

    /**
     * 当 WorkflowQueryApi bean 存在时，adapter 应正确委托调用。
     */
    @Test
    void shouldDelegateWhenBeanExists() {
        WorkflowQueryAdapter adapter = new WorkflowQueryAdapter();
        WorkflowQueryApi mockApi = mock(WorkflowQueryApi.class);
        adapter.setWorkflowQueryApi(mockApi);

        // stub countPendingTasks
        when(mockApi.countPendingTasks("E10001")).thenReturn(12);

        // stub listRecentPendingTasks
        PortalTodoItem item = new PortalTodoItem();
        item.setTaskId("T001");
        item.setTaskTitle("审批贷款申请");
        when(mockApi.listRecentPendingTasks("E10001", 5)).thenReturn(List.of(item));

        // verify delegation
        assertThat(adapter.countPending("E10001")).isEqualTo(12);

        List<PortalTodoItem> result = adapter.listPending("E10001", 5);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTaskId()).isEqualTo("T001");
        assertThat(result.get(0).getTaskTitle()).isEqualTo("审批贷款申请");

        verify(mockApi).countPendingTasks("E10001");
        verify(mockApi).listRecentPendingTasks("E10001", 5);
    }

    /**
     * 当 WorkflowQueryApi 抛出异常时，adapter 应优雅降级：
     * countPending 返回 0，listPending 返回空列表。
     */
    @Test
    void shouldReturnFallbackOnException() {
        WorkflowQueryAdapter adapter = new WorkflowQueryAdapter();
        WorkflowQueryApi mockApi = mock(WorkflowQueryApi.class);
        adapter.setWorkflowQueryApi(mockApi);

        // stub 抛出异常
        when(mockApi.countPendingTasks("E10001"))
                .thenThrow(new RuntimeException("Flowable connection failed"));
        when(mockApi.listRecentPendingTasks("E10001", 5))
                .thenThrow(new RuntimeException("Flowable connection failed"));

        // verify graceful degradation
        assertThat(adapter.countPending("E10001")).isZero();
        assertThat(adapter.listPending("E10001", 5)).isEmpty();
    }
}
