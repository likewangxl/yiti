package com.bank.branch.platform.bizapp.controller;

import com.bank.branch.platform.bizapp.api.dto.SupportRequestListItemDTO;
import com.bank.branch.platform.bizapp.enums.SupportStatus;
import com.bank.branch.platform.bizapp.service.SupportDeptService;
import com.bank.branch.platform.bizapp.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.bizapp.support.WithMockEmpContext;
import com.bank.branch.platform.common.web.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bank.branch.platform.bizapp.dto.req.DispatchReq;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SupportDeptController 集成测试。
 * 继承 AbstractControllerIntegrationTest，通过 @MockBean 替换 Service 层。
 */
class SupportDeptControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    SupportDeptService supportDeptService;

    // ==================== GET /api/support-dept/requests ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    @DisplayName("GET /api/support-dept/requests 返回 SupportRequestListItemDTO 列表，不含 deleted 字段")
    void listPageForDept_returnsListItemDTO_notEntity() throws Exception {
        SupportRequestListItemDTO item = new SupportRequestListItemDTO();
        item.setId("SR001");
        item.setRequestNo("SR20260414000001");
        item.setStatus(SupportStatus.IN_PROGRESS.getCode());
        item.setSupportDeptName("业务承接部门");
        PageResult<SupportRequestListItemDTO> page = PageResult.of(1, 20, 1L, List.of(item));

        when(supportDeptService.listPageForDeptAsDTO(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(page);

        mockMvc.perform(get("/api/support-dept/requests")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.page.records[0].supportDeptName").value("业务承接部门"))
                .andExpect(jsonPath("$.page.records[0].deleted").doesNotExist());
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void listPageForDept_shouldReturn200() throws Exception {
        SupportRequestListItemDTO item = new SupportRequestListItemDTO();
        item.setId("SR001");
        item.setStatus(SupportStatus.IN_PROGRESS.getCode());
        PageResult<SupportRequestListItemDTO> page = PageResult.of(1, 20, 1L, List.of(item));

        when(supportDeptService.listPageForDeptAsDTO(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(page);

        mockMvc.perform(get("/api/support-dept/requests")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(1));
    }

    // ==================== POST /api/support-dept/requests/{id}/dispatch ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void dispatch_shouldReturn200() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        doNothing().when(supportDeptService).dispatch(eq("SR001"), anyString(), anyString(), isNull());

        String body = "{\"assignedEmpId\":\"E20001\"}";

        mockMvc.perform(post("/api/support-dept/requests/SR001/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    @DisplayName("dispatch 必须把 dispatchRemark 透传到 Service")
    void dispatch_propagatesDispatchRemark() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        doNothing().when(supportDeptService).dispatch(anyString(), anyString(), anyString(), anyString());

        DispatchReq req = new DispatchReq();
        req.setAssignedEmpId("E20001");
        req.setDispatchRemark("请优先处理");

        mockMvc.perform(post("/api/support-dept/requests/SR001/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(supportDeptService).dispatch(eq("SR001"), eq("E20001"), eq("E10001"), eq("请优先处理"));
    }

    // ==================== POST /api/support-dept/requests/{id}/transfer ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void transfer_shouldReturn200() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        doNothing().when(supportDeptService).transfer(eq("SR001"), anyString(), anyString());

        String body = "{\"newAssignedEmpId\":\"E30001\"}";

        mockMvc.perform(post("/api/support-dept/requests/SR001/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ==================== POST /api/support-dept/requests/{id}/complete ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void complete_shouldReturn200() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        doNothing().when(supportDeptService).complete(eq("SR001"), anyBoolean(), anyString());

        String body = "{\"success\":true}";

        mockMvc.perform(post("/api/support-dept/requests/SR001/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

}
