package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.NotificationDTO;
import com.bank.branch.platform.governance.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * NotificationController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void queryNotifications_shouldReturn200WithPageResult() throws Exception {
        // given
        NotificationDTO dto = new NotificationDTO();
        dto.setId("N_001");
        dto.setTitle("测试通知");
        PageResult<NotificationDTO> pageResult = PageResult.of(1, 20, 1L, List.of(dto));
        when(notificationService.queryNotifications(anyString(), any(), anyInt(), anyInt()))
                .thenReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/notifications")
                .param("empId", "emp001")
                .param("pageNo", "1")
                .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1));
    }

    @Test
    void countUnread_shouldReturn200() throws Exception {
        // given
        when(notificationService.countUnread(anyString())).thenReturn(5);

        // when & then
        mockMvc.perform(get("/api/notifications/unread-count")
                .param("empId", "emp001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(5));
    }

    @Test
    void markAsRead_shouldReturn200() throws Exception {
        // given
        doNothing().when(notificationService).markAsRead(anyString());

        // when & then
        mockMvc.perform(put("/api/notifications/N_001/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void markAllAsRead_shouldReturn200() throws Exception {
        // given
        when(notificationService.markAllAsRead(anyString())).thenReturn(3);

        // when & then
        mockMvc.perform(put("/api/notifications/read-all")
                .param("empId", "emp001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value(3));
    }

    @Test
    void getNotificationDetail_shouldReturn200AndMarkAsRead() throws Exception {
        // given
        NotificationDTO dto = new NotificationDTO();
        dto.setId("N_001");
        dto.setTitle("测试通知");
        when(notificationService.getById(anyString())).thenReturn(dto);
        doNothing().when(notificationService).markAsRead(anyString());

        // when & then
        mockMvc.perform(get("/api/notifications/N_001")
                .param("empId", "emp001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.title").value("测试通知"));
        // 验证自动标记已读被调用
        verify(notificationService).markAsRead("N_001");
    }

    // ── L2 错误路径测试 ──────────────────────────────────────────

    @Test
    void markAsRead_notFound_returnsBizError() throws Exception {
        doThrow(new BizException("GOV-40006", "通知不存在"))
            .when(notificationService).markAsRead(anyString());

        mockMvc.perform(put("/api/notifications/NOT_EXIST/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOV-40006"));
    }
}
