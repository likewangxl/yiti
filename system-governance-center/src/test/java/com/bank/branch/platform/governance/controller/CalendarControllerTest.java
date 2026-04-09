package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.CalendarDayDTO;
import com.bank.branch.platform.governance.service.CalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CalendarController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class CalendarControllerTest {

    @Mock
    private CalendarService calendarService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CalendarController(calendarService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void getWorkingDays_shouldReturn200WithList() throws Exception {
        // given
        CalendarDayDTO dto = new CalendarDayDTO();
        dto.setDay(LocalDate.of(2026, 1, 1));
        dto.setIsWorkday(0);
        when(calendarService.getWorkingDays(anyInt())).thenReturn(List.of(dto));

        // when & then
        mockMvc.perform(get("/api/admin/sys/calendar")
                .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getWorkingDays_withMonth_shouldReturn200WithList() throws Exception {
        // given
        CalendarDayDTO dto = new CalendarDayDTO();
        dto.setDay(LocalDate.of(2026, 3, 1));
        dto.setIsWorkday(0);
        when(calendarService.getDaysByMonth(anyInt(), anyInt())).thenReturn(List.of(dto));

        // when & then
        mockMvc.perform(get("/api/admin/sys/calendar")
                .param("year", "2026")
                .param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());
        // 验证调用了 getDaysByMonth 而非 getWorkingDays
        verify(calendarService).getDaysByMonth(2026, 3);
        verify(calendarService, never()).getWorkingDays(anyInt());
    }

    @Test
    void setWorkday_shouldReturn200() throws Exception {
        // given
        doNothing().when(calendarService).setWorkday(any(LocalDate.class), anyBoolean(), any());

        // when & then
        mockMvc.perform(put("/api/admin/sys/calendar/2026-05-01")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isWorkday\": true, \"remark\": \"调休\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void initYear_shouldReturn200() throws Exception {
        // given
        doNothing().when(calendarService).initYear(anyInt());

        // when & then
        mockMvc.perform(post("/api/admin/sys/calendar/init")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"year\": 2026}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ── L2 错误路径测试 ──────────────────────────────────────────

    @Test
    void setWorkday_pastDate_returnsBizError() throws Exception {
        doThrow(new BizException("GOV-40301", "过去日期不可修改"))
            .when(calendarService).setWorkday(any(LocalDate.class), anyBoolean(), any());

        mockMvc.perform(put("/api/admin/sys/calendar/2020-01-01")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isWorkday\": true, \"remark\": \"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOV-40301"));
    }
}
