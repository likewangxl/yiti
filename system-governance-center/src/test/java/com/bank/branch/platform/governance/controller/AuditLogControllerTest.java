package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.governance.api.dto.AuditLogDTO;
import com.bank.branch.platform.governance.api.dto.AuditLogQueryReqDTO;
import com.bank.branch.platform.governance.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuditLogController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    @Mock
    private AuditLogService auditLogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuditLogController(auditLogService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void queryLogs_shouldReturn200WithPageResult() throws Exception {
        // given
        AuditLogDTO dto = new AuditLogDTO();
        dto.setId("AL_001");
        dto.setEmpId("emp001");
        PageResult<AuditLogDTO> pageResult = PageResult.of(1, 20, 1L, List.of(dto));
        when(auditLogService.queryLogs(any(AuditLogQueryReqDTO.class), anyInt(), anyInt()))
                .thenReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/admin/sys/audit-logs")
                .param("pageNo", "1")
                .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1));
    }

    @Test
    void queryLogs_withFilters_shouldReturn200() throws Exception {
        // given
        PageResult<AuditLogDTO> pageResult = PageResult.of(1, 20, 0L, List.of());
        when(auditLogService.queryLogs(any(AuditLogQueryReqDTO.class), anyInt(), anyInt()))
                .thenReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/admin/sys/audit-logs")
                .param("empId", "emp001")
                .param("bizType", "SYS_CONFIG")
                .param("startTime", "2026-01-01")
                .param("endTime", "2026-12-31")
                .param("pageNo", "1")
                .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }
}
