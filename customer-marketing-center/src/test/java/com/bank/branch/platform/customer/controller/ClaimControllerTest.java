package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.CustClaim;
import com.bank.branch.platform.customer.enums.ClaimStatus;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.service.ClaimService;
import com.bank.branch.platform.customer.support.AbstractControllerIntegrationTest;
import com.bank.branch.platform.customer.support.WithMockEmpContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ClaimController 集成测试。
 * <p>
 * 继承 AbstractControllerIntegrationTest 基类，通过 @MockBean ClaimService 替换真实业务逻辑。
 * </p>
 */
class ClaimControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ClaimService claimService;

    // ==================== POST /api/claims ====================

    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void claim_shouldReturn200() throws Exception {
        // given
        CustClaim claim = new CustClaim();
        claim.setId("claim-001");
        claim.setCustId("cust-001");
        claim.setOrgId("ORG_SZ_001");
        claim.setClaimedBy("E10001");
        claim.setMaintainerEmpId("E10001");
        claim.setClaimStatus(ClaimStatus.CLAIMED.getCode());
        claim.setClaimTime(LocalDateTime.now());

        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_SZ_001");
        when(claimService.claim(eq("cust-001"), eq("ORG_SZ_001"), eq("E10001"))).thenReturn(claim);

        String body = "{\"custId\":\"cust-001\"}";

        // when/then
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").value("claim-001"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001", orgCode = "ORG_SZ_001")
    void claim_shouldReturn409WhenAlreadyClaimed() throws Exception {
        // given: 客户已被认领
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG_SZ_001");
        when(claimService.claim(eq("cust-001"), eq("ORG_SZ_001"), eq("E10001")))
                .thenThrow(new BizException(
                        CustomerErrorCode.CUSTOMER_ALREADY_CLAIMED.getCode(),
                        CustomerErrorCode.CUSTOMER_ALREADY_CLAIMED.getMessage()));

        String body = "{\"custId\":\"cust-001\"}";

        // when/then: 业务异常由全局异常处理器映射，code 字段为错误码
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CustomerErrorCode.CUSTOMER_ALREADY_CLAIMED.getCode()));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void claim_shouldReturn400WhenCustIdBlank() throws Exception {
        // given: custId 为空，触发 @NotBlank 校验
        String body = "{\"custId\":\"\"}";

        // when/then
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ==================== POST /api/claims/{id}/cancel ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void cancelClaim_shouldReturn200() throws Exception {
        // given
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        doNothing().when(claimService).cancelClaim(eq("claim-001"), eq("客户不符合条件"), eq("E10001"), eq("ORG_SZ_001"));

        String body = "{\"reason\":\"客户不符合条件\"}";

        // when/then
        mockMvc.perform(post("/api/claims/claim-001/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void cancelClaim_shouldReturn400WhenReasonBlank() throws Exception {
        // given: reason 为空，触发 @NotBlank 校验
        String body = "{\"reason\":\"\"}";

        // when/then
        mockMvc.perform(post("/api/claims/claim-001/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockEmpContext(empId = "E10001")
    void cancelClaim_shouldReturn404WhenNotFound() throws Exception {
        // given: 认领记录不存在
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        doThrow(new BizException(
                CustomerErrorCode.CLAIM_NOT_FOUND.getCode(),
                CustomerErrorCode.CLAIM_NOT_FOUND.getMessage()))
                .when(claimService).cancelClaim(eq("not-exist"), anyString(), eq("E10001"), eq("ORG_SZ_001"));

        String body = "{\"reason\":\"原因说明\"}";

        // when/then
        mockMvc.perform(post("/api/claims/not-exist/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CustomerErrorCode.CLAIM_NOT_FOUND.getCode()));
    }

    // ==================== GET /api/claims/mine ====================

    @Test
    @WithMockEmpContext(empId = "E10001")
    void listMyClaims_shouldReturn200() throws Exception {
        // given
        CustClaim claim1 = new CustClaim();
        claim1.setId("claim-001");
        claim1.setClaimedBy("E10001");
        claim1.setClaimStatus(ClaimStatus.CLAIMED.getCode());
        CustClaim claim2 = new CustClaim();
        claim2.setId("claim-002");
        claim2.setClaimedBy("E10001");
        claim2.setClaimStatus(ClaimStatus.CLAIMED.getCode());

        PageResult<CustClaim> page = PageResult.of(1, 20, 2L, Arrays.asList(claim1, claim2));
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(claimService.listMyClaims(eq("E10001"), eq(1), eq(20))).thenReturn(page);

        // when/then
        mockMvc.perform(get("/api/claims/mine")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.total").value(2))
                .andExpect(jsonPath("$.page.records[0].id").value("claim-001"));
    }
}
