package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.portal.api.dto.*;
import com.bank.branch.platform.portal.service.NavService;
import com.bank.branch.platform.portal.service.ProductExportService;
import com.bank.branch.platform.portal.service.ProductService;
import com.bank.branch.platform.portal.service.ShortcutService;
import com.bank.branch.platform.portal.service.WorkspaceService;
import com.bank.branch.platform.portal.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.portal.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WorkspaceController 集成测试
 *
 * <p>使用 @MockBean 替换 WorkspaceService，验证 GET /api/portal/workspace 接口的请求处理。
 * 同时 mock 其他 Service 避免拖入数据库依赖。</p>
 */
class WorkspaceControllerTest extends AbstractControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @MockBean WorkspaceService workspaceService;
    @MockBean ShortcutService shortcutService;
    @MockBean NavService navService;
    @MockBean ProductService productService;
    @MockBean ProductExportService productExportService;

    // ────────── 1. 正常数据场景 ──────────

    /**
     * GET /api/portal/workspace 返回 200，验证 $.data 中各字段存在且正确
     */
    @Test
    @WithMockEmpContext
    void getWorkspaceShouldReturn200WithWorkspaceData() throws Exception {
        ShortcutDTO shortcut = ShortcutDTO.builder()
                .id("s1").shortcutName("核心系统").shortcutUrl("https://core.bank.com")
                .shortcutIcon("icon-core").shortcutType("SYSTEM").targetType("INTERNAL").sortOrder(1)
                .build();

        TodoItemDTO todo = TodoItemDTO.builder()
                .taskId("T001").processInstanceId("PI001").processName("请假审批")
                .taskTitle("审批张三的请假申请").initiatorName("张三")
                .initiatedTime(LocalDateTime.of(2025, 6, 1, 10, 30, 0))
                .lightStatus("GREEN").overdueInfo(null).bizDetailUrl("/leave/PI001")
                .build();

        NotificationItemDTO notif = NotificationItemDTO.builder()
                .notificationId("N001").title("系统通知").summary("系统将于今晚维护")
                .sentTime(LocalDateTime.of(2025, 6, 1, 9, 0, 0))
                .readStatus("UNREAD").bizType("SYSTEM").bizId("BIZ001")
                .bizDetailUrl("/notifications/N001")
                .build();

        PortalMetricCard card = PortalMetricCard.builder()
                .metricCode("DEPOSIT")
                .metricName("存款余额")
                .currentValue("1000000.00")
                .targetValue("2000000.00")
                .completionRate(new BigDecimal("50.00"))
                .trend("UP")
                .unit("元")
                .build();

        WorkspaceDTO dto = WorkspaceDTO.builder()
                .todoCount(3)
                .unreadNotificationCount(5)
                .recentTodos(List.of(todo))
                .recentNotifications(List.of(notif))
                .metricCards(List.of(card))
                .shortcuts(List.of(shortcut))
                .aggregateErrors(Collections.emptyMap())
                .build();

        when(workspaceService.getWorkspace()).thenReturn(dto);

        mockMvc.perform(get("/api/portal/workspace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.todoCount").value(3))
                .andExpect(jsonPath("$.data.unreadNotificationCount").value(5))
                .andExpect(jsonPath("$.data.shortcuts").isArray())
                .andExpect(jsonPath("$.data.shortcuts[0].shortcutName").value("核心系统"))
                .andExpect(jsonPath("$.data.recentTodos").isArray())
                .andExpect(jsonPath("$.data.recentTodos[0].taskId").value("T001"))
                .andExpect(jsonPath("$.data.recentNotifications").isArray())
                .andExpect(jsonPath("$.data.recentNotifications[0].notificationId").value("N001"))
                .andExpect(jsonPath("$.data.metricCards").isArray())
                .andExpect(jsonPath("$.data.metricCards[0].metricCode").value("DEPOSIT"));
    }

    // ────────── 2. 空数据场景 ──────────

    /**
     * GET /api/portal/workspace 所有区域为空时返回 200，验证各字段为默认值
     */
    @Test
    @WithMockEmpContext
    void getWorkspaceShouldReturn200WithEmptyWorkspace() throws Exception {
        WorkspaceDTO emptyDto = WorkspaceDTO.builder()
                .todoCount(0)
                .unreadNotificationCount(0)
                .recentTodos(Collections.emptyList())
                .recentNotifications(Collections.emptyList())
                .metricCards(Collections.emptyList())
                .shortcuts(Collections.emptyList())
                .aggregateErrors(Collections.emptyMap())
                .build();

        when(workspaceService.getWorkspace()).thenReturn(emptyDto);

        mockMvc.perform(get("/api/portal/workspace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.todoCount").value(0))
                .andExpect(jsonPath("$.data.unreadNotificationCount").value(0))
                .andExpect(jsonPath("$.data.shortcuts").isEmpty())
                .andExpect(jsonPath("$.data.recentTodos").isEmpty())
                .andExpect(jsonPath("$.data.recentNotifications").isEmpty())
                .andExpect(jsonPath("$.data.metricCards").isEmpty());
    }

    // ────────── 3. 包含降级错误信息场景 ──────────

    /**
     * GET /api/portal/workspace 包含 aggregateErrors 时返回 200，验证错误信息存在
     */
    @Test
    @WithMockEmpContext
    void getWorkspaceShouldReturn200WithAggregateErrors() throws Exception {
        WorkspaceDTO dtoWithErrors = WorkspaceDTO.builder()
                .todoCount(0)
                .unreadNotificationCount(0)
                .recentTodos(Collections.emptyList())
                .recentNotifications(Collections.emptyList())
                .metricCards(Collections.emptyList())
                .shortcuts(Collections.emptyList())
                .aggregateErrors(Map.of(
                        "unreadNotificationCount", "NotifyApi unavailable",
                        "shortcuts", "ShortcutService error"
                ))
                .build();

        when(workspaceService.getWorkspace()).thenReturn(dtoWithErrors);

        mockMvc.perform(get("/api/portal/workspace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.todoCount").value(0))
                .andExpect(jsonPath("$.data.aggregateErrors.unreadNotificationCount").value("NotifyApi unavailable"))
                .andExpect(jsonPath("$.data.aggregateErrors.shortcuts").value("ShortcutService error"));
    }
}
