package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.req.ReTouchReqDTO;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.TouchTaskStatus;
import com.bank.branch.platform.customer.enums.TouchTaskType;
import com.bank.branch.platform.customer.service.ClaimService;
import com.bank.branch.platform.customer.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.customer.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ClaimController.reTouch 集成测试（POST /api/claims/{claimId}/re-touch）。
 */
class ClaimControllerReTouchTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ClaimService claimService;

    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void reTouch_success_returns200() throws Exception {
        TouchTask created = new TouchTask();
        created.setId("touch-new-001");
        created.setTaskNo("TOUCH_TEST_0001");
        created.setTaskType(TouchTaskType.FOLLOW_UP.getCode());
        created.setTaskStatus(TouchTaskStatus.PENDING.getCode());

        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_SZ_001");
        when(claimService.reTouch(eq("claim-001"), any(ReTouchReqDTO.class),
                eq("E10001"), eq("ORG_SZ_001"))).thenReturn(created);

        String body = "{\"reason\":\"客户提出新需求需要重新触达\"}";

        mockMvc.perform(post("/api/claims/claim-001/re-touch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("touch-new-001"))
                .andExpect(jsonPath("$.data.taskType").value("FOLLOW_UP"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void reTouch_blankReason_returns400() throws Exception {
        // reason 缺失 → @Valid + @NotBlank 校验失败，返回真 HTTP 400
        String body = "{\"reason\":\"\"}";

        mockMvc.perform(post("/api/claims/claim-001/re-touch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void reTouch_claimNotFound_returnsCust40404() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_SZ_001");
        doThrow(new BizException(
                CustomerErrorCode.CLAIM_NOT_FOUND.getCode(),
                CustomerErrorCode.CLAIM_NOT_FOUND.getMessage()))
                .when(claimService).reTouch(eq("not-exist"), any(ReTouchReqDTO.class),
                        eq("E10001"), eq("ORG_SZ_001"));

        String body = "{\"reason\":\"任意原因满足十字以上校验\"}";

        mockMvc.perform(post("/api/claims/not-exist/re-touch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CustomerErrorCode.CLAIM_NOT_FOUND.getCode()));
    }

    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void reTouch_runningTask_returnsCust40908() throws Exception {
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_SZ_001");
        doThrow(new BizException(
                CustomerErrorCode.RE_TOUCH_HAS_RUNNING.getCode(),
                CustomerErrorCode.RE_TOUCH_HAS_RUNNING.getMessage()))
                .when(claimService).reTouch(eq("claim-001"), any(ReTouchReqDTO.class),
                        eq("E10001"), eq("ORG_SZ_001"));

        String body = "{\"reason\":\"任意原因满足十字以上校验\"}";

        mockMvc.perform(post("/api/claims/claim-001/re-touch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CustomerErrorCode.RE_TOUCH_HAS_RUNNING.getCode()));
    }
}
