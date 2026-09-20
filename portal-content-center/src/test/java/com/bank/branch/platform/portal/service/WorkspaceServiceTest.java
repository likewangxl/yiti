package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.governance.api.dto.NotificationDTO;
import com.bank.branch.platform.portal.adapter.MetricAdapter;
import com.bank.branch.platform.portal.adapter.WorkflowQueryAdapter;
import com.bank.branch.platform.portal.adapter.dto.PortalTodoItem;
import com.bank.branch.platform.portal.api.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * WorkspaceService 单元测试
 *
 * <p>使用 MockitoExtension 纯单元测试，传入同步 Executor 避免异步测试不稳定。</p>
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private ShortcutService shortcutService;
    @Mock
    private NotifyApi notifyApi;
    @Mock
    private WorkflowQueryAdapter workflowQueryAdapter;
    @Mock
    private MetricAdapter metricAdapter;
    @Mock
    private CurrentUserApi currentUserApi;

    private WorkspaceService workspaceService;

    private static final String EMP_ID = "E10001";

    @BeforeEach
    void setUp() {
        // 使用同步 Executor 避免异步测试不稳定
        Executor syncExecutor = Runnable::run;
        workspaceService = new WorkspaceService(
                shortcutService, notifyApi, workflowQueryAdapter,
                metricAdapter, currentUserApi, syncExecutor);
    }

    /** 供 getWorkspace() 系列测试调用，stub currentUserApi 返回固定 empId */
    private void stubCurrentUser() {
        when(currentUserApi.getCurrentEmpId()).thenReturn(EMP_ID);
    }

    // ────────── 1. 所有源正常返回数据，验证聚合结果 ──────────

    /**
     * 所有 mock 返回正常数据，验证 WorkspaceDTO 7 个字段都正确填充，aggregateErrors 为空
     */
    @Test
    void getWorkspaceShouldAggregateAllSources() {
        stubCurrentUser();
        // shortcuts
        ShortcutDTO shortcut = ShortcutDTO.builder()
                .id("s1").shortcutName("核心系统").shortcutUrl("https://core.bank.com")
                .shortcutIcon("icon-core").shortcutType("SYSTEM").targetType("INTERNAL").sortOrder(1)
                .build();
        when(shortcutService.listMyShortcuts()).thenReturn(List.of(shortcut));

        // todoCount
        when(workflowQueryAdapter.countPending("E10001")).thenReturn(3);

        // recentTodos
        PortalTodoItem todoItem = new PortalTodoItem();
        todoItem.setTaskId("T001");
        todoItem.setProcessInstanceId("PI001");
        todoItem.setProcessName("请假审批");
        todoItem.setTaskTitle("审批张三的请假申请");
        todoItem.setInitiatorName("张三");
        todoItem.setInitiatedTime("2025-06-01T10:30:00");
        todoItem.setLightStatus("GREEN");
        todoItem.setOverdueInfo(null);
        todoItem.setBizDetailUrl("/leave/PI001");
        when(workflowQueryAdapter.listPending("E10001", 5)).thenReturn(List.of(todoItem));

        // unreadNotificationCount
        when(notifyApi.countUnread("E10001")).thenReturn(5);

        // recentNotifications
        NotificationDTO notifDTO = new NotificationDTO();
        notifDTO.setId("N001");
        notifDTO.setTitle("系统通知");
        notifDTO.setContent("系统将于今晚维护");
        notifDTO.setCreatedTime("2025-06-01T09:00:00");
        notifDTO.setIsRead(false);
        notifDTO.setBizType("SYSTEM");
        notifDTO.setBizId("BIZ001");
        notifDTO.setLinkUrl("/notifications/N001");
        PageResult<NotificationDTO> notifPage = PageResult.of(1, 5, 1, List.of(notifDTO));
        when(notifyApi.queryNotifications(eq("E10001"), isNull(), any(PageRequest.class)))
                .thenReturn(notifPage);

        // metricCards
        PortalMetricCard card = PortalMetricCard.builder()
                .metricCode("DEPOSIT")
                .metricName("存款余额")
                .currentValue("1000000.00")
                .targetValue("2000000.00")
                .completionRate(new BigDecimal("50.00"))
                .trend("UP")
                .unit("元")
                .build();
        when(metricAdapter.fetchForWorkspace("E10001")).thenReturn(List.of(card));

        // 执行
        WorkspaceDTO result = workspaceService.getWorkspace();

        // 验证
        assertThat(result.getTodoCount()).isEqualTo(3);
        assertThat(result.getUnreadNotificationCount()).isEqualTo(5);

        assertThat(result.getShortcuts()).hasSize(1);
        assertThat(result.getShortcuts().get(0).getShortcutName()).isEqualTo("核心系统");

        assertThat(result.getRecentTodos()).hasSize(1);
        TodoItemDTO todo = result.getRecentTodos().get(0);
        assertThat(todo.getTaskId()).isEqualTo("T001");
        assertThat(todo.getProcessName()).isEqualTo("请假审批");
        assertThat(todo.getInitiatedTime()).isEqualTo(LocalDateTime.of(2025, 6, 1, 10, 30, 0));

        assertThat(result.getRecentNotifications()).hasSize(1);
        NotificationItemDTO notif = result.getRecentNotifications().get(0);
        assertThat(notif.getNotificationId()).isEqualTo("N001");
        assertThat(notif.getReadStatus()).isEqualTo("UNREAD");
        assertThat(notif.getSentTime()).isEqualTo(LocalDateTime.of(2025, 6, 1, 9, 0, 0));

        assertThat(result.getMetricCards()).hasSize(1);
        assertThat(result.getMetricCards().get(0).getMetricCode()).isEqualTo("DEPOSIT");

        assertThat(result.getAggregateErrors()).isEmpty();
    }

    // ────────── 2. 所有源返回空数据，验证默认值 ──────────

    /**
     * 所有源返回空数据(0, emptyList)，验证返回正确默认值
     */
    @Test
    void getWorkspaceShouldReturnEmptyFallbackWhenAllSourcesEmpty() {
        stubCurrentUser();
        when(shortcutService.listMyShortcuts()).thenReturn(Collections.emptyList());
        when(workflowQueryAdapter.countPending("E10001")).thenReturn(0);
        when(workflowQueryAdapter.listPending("E10001", 5)).thenReturn(Collections.emptyList());
        when(notifyApi.countUnread("E10001")).thenReturn(0);
        PageResult<NotificationDTO> emptyPage = PageResult.of(1, 5, 0, Collections.emptyList());
        when(notifyApi.queryNotifications(eq("E10001"), isNull(), any(PageRequest.class)))
                .thenReturn(emptyPage);
        when(metricAdapter.fetchForWorkspace("E10001")).thenReturn(Collections.emptyList());

        WorkspaceDTO result = workspaceService.getWorkspace();

        assertThat(result.getTodoCount()).isEqualTo(0);
        assertThat(result.getUnreadNotificationCount()).isEqualTo(0);
        assertThat(result.getShortcuts()).isEmpty();
        assertThat(result.getRecentTodos()).isEmpty();
        assertThat(result.getRecentNotifications()).isEmpty();
        assertThat(result.getMetricCards()).isEmpty();
        assertThat(result.getAggregateErrors()).isEmpty();
    }

    // ────────── 3. NotifyApi 异常时降级 ──────────

    /**
     * notifyApi.countUnread 抛异常，验证 unreadNotificationCount 为 0，aggregateErrors 包含对应 key
     */
    @Test
    void getWorkspaceShouldCollectErrorWhenNotifyApiFails() {
        stubCurrentUser();
        when(shortcutService.listMyShortcuts()).thenReturn(Collections.emptyList());
        when(workflowQueryAdapter.countPending("E10001")).thenReturn(0);
        when(workflowQueryAdapter.listPending("E10001", 5)).thenReturn(Collections.emptyList());
        when(notifyApi.countUnread("E10001")).thenThrow(new RuntimeException("NotifyApi unavailable"));
        PageResult<NotificationDTO> emptyPage = PageResult.of(1, 5, 0, Collections.emptyList());
        when(notifyApi.queryNotifications(eq("E10001"), isNull(), any(PageRequest.class)))
                .thenReturn(emptyPage);
        when(metricAdapter.fetchForWorkspace("E10001")).thenReturn(Collections.emptyList());

        WorkspaceDTO result = workspaceService.getWorkspace();

        assertThat(result.getUnreadNotificationCount()).isEqualTo(0);
        assertThat(result.getAggregateErrors()).containsKey("unreadNotificationCount");
    }

    // ────────── 4. ShortcutService 异常时降级 ──────────

    /**
     * shortcutService 抛异常，验证 shortcuts 为空列表，aggregateErrors 包含 "shortcuts"
     */
    @Test
    void getWorkspaceShouldCollectErrorWhenShortcutServiceFails() {
        stubCurrentUser();
        when(shortcutService.listMyShortcuts()).thenThrow(new RuntimeException("ShortcutService error"));
        when(workflowQueryAdapter.countPending("E10001")).thenReturn(0);
        when(workflowQueryAdapter.listPending("E10001", 5)).thenReturn(Collections.emptyList());
        when(notifyApi.countUnread("E10001")).thenReturn(0);
        PageResult<NotificationDTO> emptyPage = PageResult.of(1, 5, 0, Collections.emptyList());
        when(notifyApi.queryNotifications(eq("E10001"), isNull(), any(PageRequest.class)))
                .thenReturn(emptyPage);
        when(metricAdapter.fetchForWorkspace("E10001")).thenReturn(Collections.emptyList());

        WorkspaceDTO result = workspaceService.getWorkspace();

        assertThat(result.getShortcuts()).isEmpty();
        assertThat(result.getAggregateErrors()).containsKey("shortcuts");
    }

    @Test
    void getWorkspaceShouldCollectErrorWhenMetricAdapterFails() {
        stubCurrentUser();
        when(shortcutService.listMyShortcuts()).thenReturn(Collections.emptyList());
        when(workflowQueryAdapter.countPending("E10001")).thenReturn(0);
        when(workflowQueryAdapter.listPending("E10001", 5)).thenReturn(Collections.emptyList());
        when(notifyApi.countUnread("E10001")).thenReturn(0);
        PageResult<NotificationDTO> emptyPage = PageResult.of(1, 5, 0, Collections.emptyList());
        when(notifyApi.queryNotifications(eq("E10001"), isNull(), any(PageRequest.class)))
                .thenReturn(emptyPage);
        when(metricAdapter.fetchForWorkspace("E10001"))
                .thenThrow(new IllegalStateException("MetricApi bean is unavailable"));

        WorkspaceDTO result = workspaceService.getWorkspace();

        assertThat(result.getMetricCards()).isEmpty();
        assertThat(result.getAggregateErrors()).containsKey("metricCards");
    }

    // ────────── 5. PortalTodoItem → TodoItemDTO 转换 ──────────

    /**
     * 测试 PortalTodoItem → TodoItemDTO 转换，包括 initiatedTime String→LocalDateTime
     */
    @Test
    void toTodoItemDTOShouldConvertFieldsCorrectly() {
        stubCurrentUser();
        PortalTodoItem item = new PortalTodoItem();
        item.setTaskId("T100");
        item.setProcessInstanceId("PI100");
        item.setProcessName("报销审批");
        item.setTaskTitle("审批李四的报销单");
        item.setInitiatorName("李四");
        item.setInitiatedTime("2025-03-15T14:00:00");
        item.setLightStatus("YELLOW");
        item.setOverdueInfo("即将超期");
        item.setBizDetailUrl("/expense/PI100");

        when(workflowQueryAdapter.countPending("E10001")).thenReturn(1);
        when(workflowQueryAdapter.listPending("E10001", 5)).thenReturn(List.of(item));
        when(shortcutService.listMyShortcuts()).thenReturn(Collections.emptyList());
        when(notifyApi.countUnread("E10001")).thenReturn(0);
        PageResult<NotificationDTO> emptyPage = PageResult.of(1, 5, 0, Collections.emptyList());
        when(notifyApi.queryNotifications(eq("E10001"), isNull(), any(PageRequest.class)))
                .thenReturn(emptyPage);
        when(metricAdapter.fetchForWorkspace("E10001")).thenReturn(Collections.emptyList());

        WorkspaceDTO result = workspaceService.getWorkspace();

        assertThat(result.getRecentTodos()).hasSize(1);
        TodoItemDTO dto = result.getRecentTodos().get(0);
        assertThat(dto.getTaskId()).isEqualTo("T100");
        assertThat(dto.getProcessInstanceId()).isEqualTo("PI100");
        assertThat(dto.getProcessName()).isEqualTo("报销审批");
        assertThat(dto.getTaskTitle()).isEqualTo("审批李四的报销单");
        assertThat(dto.getInitiatorName()).isEqualTo("李四");
        assertThat(dto.getInitiatedTime()).isEqualTo(LocalDateTime.of(2025, 3, 15, 14, 0, 0));
        assertThat(dto.getLightStatus()).isEqualTo("YELLOW");
        assertThat(dto.getOverdueInfo()).isEqualTo("即将超期");
        assertThat(dto.getBizDetailUrl()).isEqualTo("/expense/PI100");
    }

    // ────────── 6. NotificationDTO → NotificationItemDTO 转换 ──────────

    /**
     * 测试 NotificationDTO → NotificationItemDTO 转换，包括 isRead→readStatus, createdTime→sentTime
     */
    @Test
    void toNotificationItemDTOShouldConvertFieldsCorrectly() {
        stubCurrentUser();
        NotificationDTO notifDTO = new NotificationDTO();
        notifDTO.setId("N200");
        notifDTO.setTitle("审批通知");
        notifDTO.setContent("您的请假已通过");
        notifDTO.setCreatedTime("2025-04-20T16:30:00");
        notifDTO.setIsRead(true);
        notifDTO.setBizType("WORKFLOW");
        notifDTO.setBizId("WF200");
        notifDTO.setLinkUrl("/workflow/WF200");

        PageResult<NotificationDTO> notifPage = PageResult.of(1, 5, 1, List.of(notifDTO));

        when(shortcutService.listMyShortcuts()).thenReturn(Collections.emptyList());
        when(workflowQueryAdapter.countPending("E10001")).thenReturn(0);
        when(workflowQueryAdapter.listPending("E10001", 5)).thenReturn(Collections.emptyList());
        when(notifyApi.countUnread("E10001")).thenReturn(0);
        when(notifyApi.queryNotifications(eq("E10001"), isNull(), any(PageRequest.class)))
                .thenReturn(notifPage);
        when(metricAdapter.fetchForWorkspace("E10001")).thenReturn(Collections.emptyList());

        WorkspaceDTO result = workspaceService.getWorkspace();

        assertThat(result.getRecentNotifications()).hasSize(1);
        NotificationItemDTO dto = result.getRecentNotifications().get(0);
        assertThat(dto.getNotificationId()).isEqualTo("N200");
        assertThat(dto.getTitle()).isEqualTo("审批通知");
        assertThat(dto.getSummary()).isEqualTo("您的请假已通过");
        assertThat(dto.getSentTime()).isEqualTo(LocalDateTime.of(2025, 4, 20, 16, 30, 0));
        assertThat(dto.getReadStatus()).isEqualTo("READ");
        assertThat(dto.getBizType()).isEqualTo("WORKFLOW");
        assertThat(dto.getBizId()).isEqualTo("WF200");
        assertThat(dto.getBizDetailUrl()).isEqualTo("/workflow/WF200");
    }

    // ────────── 7. NotifyApi 为 null（V1 降级） ──────────

    /**
     * notifyApi 为 null 时（V1 阶段 governance 未接入），验证 unreadNotificationCount=0, recentNotifications=[]
     */
    @Test
    void getWorkspaceShouldDegradeGracefullyWhenNotifyApiIsNull() {
        stubCurrentUser();
        // 构建一个 notifyApi=null 的 WorkspaceService
        Executor syncExecutor = Runnable::run;
        WorkspaceService serviceWithNullNotify = new WorkspaceService(
                shortcutService, null, workflowQueryAdapter,
                metricAdapter, currentUserApi, syncExecutor);

        when(shortcutService.listMyShortcuts()).thenReturn(Collections.emptyList());
        when(workflowQueryAdapter.countPending(EMP_ID)).thenReturn(0);
        when(workflowQueryAdapter.listPending(EMP_ID, 5)).thenReturn(Collections.emptyList());
        when(metricAdapter.fetchForWorkspace(EMP_ID)).thenReturn(Collections.emptyList());

        WorkspaceDTO result = serviceWithNullNotify.getWorkspace();

        assertThat(result.getUnreadNotificationCount()).isEqualTo(0);
        assertThat(result.getRecentNotifications()).isEmpty();
        assertThat(result.getAggregateErrors()).isEmpty();
    }

    // ────────── 8. getTodoCount 委托 ──────────

    /**
     * getTodoCount 委托给 workflowQueryAdapter.countPending
     */
    @Test
    void getTodoCountShouldDelegateToWorkflowQueryAdapter() {
        when(workflowQueryAdapter.countPending("E10001")).thenReturn(7);

        int count = workspaceService.getTodoCount("E10001");

        assertThat(count).isEqualTo(7);
    }

    // ────────── 9. getUnreadNotificationCount 正常委托 ──────────

    /**
     * getUnreadNotificationCount 委托给 notifyApi.countUnread
     */
    @Test
    void getUnreadNotificationCountShouldDelegateToNotifyApi() {
        when(notifyApi.countUnread("E10001")).thenReturn(12);

        int count = workspaceService.getUnreadNotificationCount("E10001");

        assertThat(count).isEqualTo(12);
    }

    // ────────── 10. getUnreadNotificationCount notifyApi 为 null ──────────

    /**
     * notifyApi 为 null 时 getUnreadNotificationCount 返回 0
     */
    @Test
    void getUnreadNotificationCountShouldReturnZeroWhenNotifyApiIsNull() {
        Executor syncExecutor = Runnable::run;
        WorkspaceService serviceWithNullNotify = new WorkspaceService(
                shortcutService, null, workflowQueryAdapter,
                metricAdapter, currentUserApi, syncExecutor);

        int count = serviceWithNullNotify.getUnreadNotificationCount("E10001");

        assertThat(count).isEqualTo(0);
    }
}
