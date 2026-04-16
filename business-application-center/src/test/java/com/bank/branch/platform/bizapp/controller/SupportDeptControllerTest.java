package com.bank.branch.platform.bizapp.controller;

import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.enums.SupportStatus;
import com.bank.branch.platform.bizapp.service.SupportDeptService;
import com.bank.branch.platform.bizapp.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.bizapp.support.WithMockEmpContext;
import com.bank.branch.platform.common.web.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
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

    @MockBean
    SupportDeptService supportDeptService;

    // ==================== GET /api/support-dept/requests ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void listPageForDept_shouldReturn200() throws Exception {
        SupportRequest sr = buildRequest("SR001");
        PageResult<SupportRequest> page = PageResult.of(1, 20, 1L, List.of(sr));

        when(supportDeptService.listPageForDept(any(), any(), any(), anyInt(), anyInt()))
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
        doNothing().when(supportDeptService).dispatch(eq("SR001"), anyString(), anyString());

        String body = "{\"assignedEmpId\":\"E20001\"}";

        mockMvc.perform(post("/api/support-dept/requests/SR001/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
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

    // ==================== 辅助方法 ====================

    private SupportRequest buildRequest(String id) {
        SupportRequest sr = new SupportRequest();
        sr.setId(id);
        sr.setRequestNo("SR20260414000001");
        sr.setCustId("CUST001");
        sr.setStatus(SupportStatus.IN_PROGRESS.getCode());
        sr.setSupportDeptId("DEPT001");
        sr.setOwnerOrgId("ORG001");
        sr.setDeleted(0);
        return sr;
    }
}
