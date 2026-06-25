package com.bank.branch.platform.soap.controller;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.performance.api.AllocApi;
import com.bank.branch.platform.performance.api.CustStatQueryApi;
import com.bank.branch.platform.performance.api.PerfApprovalCmdApi;
import com.bank.branch.platform.performance.api.PerfApprovalQueryApi;
import com.bank.branch.platform.soap.config.CallPuContentTypeNormalizationFilter;
import com.bank.branch.platform.soap.controller.dto.CallPuRequest;
import com.bank.branch.platform.soap.service.CallPuDispatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 callpu 入口兼容 {@code application/x-www-form-urlencoded} 请求头（SYS_415 修复）。
 *
 * <p>复现真实场景：手机端 / ICPS 旧网关以表单 Content-Type 提交、但请求体是 JSON 文本。
 * 未加 {@link CallPuContentTypeNormalizationFilter} 时该请求会被 Spring 在入口处以 415 拒绝；
 * 加上后过滤器把 Content-Type 归一化为 {@code application/json}，Jackson 正常解析。</p>
 */
@ExtendWith(MockitoExtension.class)
class CallPuFormUrlencodedCompatTest {

    @Mock
    private PerfApprovalQueryApi perfApprovalQueryApi;

    @Mock
    private PerfApprovalCmdApi perfApprovalCmdApi;

    @Mock
    private CustStatQueryApi custStatQueryApi;

    @Mock
    private UserApi userApi;

    @Mock
    private AllocApi allocApi;

    @Mock
    private DictApi dictApi;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();


    @BeforeEach
    void setUp() {
        CallPuDispatchService dispatchService = new CallPuDispatchService(
                perfApprovalQueryApi, perfApprovalCmdApi, custStatQueryApi, userApi, allocApi, dictApi);
        mockMvc = MockMvcBuilders.standaloneSetup(new CallPuController(dispatchService))
                .addFilters(new CallPuContentTypeNormalizationFilter())
                .build();
    }

    @Test
    void formUrlencodedContentType_withJsonBody_isAcceptedNot415() throws Exception {
        when(userApi.mapUsernamesToEmpId(List.of("E001"))).thenReturn(Map.of("E001", "U001"));
        when(perfApprovalQueryApi.listAllocAdjustApprovals(eq("U001"), eq(null), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 100, 0L, List.of()));

        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_LIST");
        req.setParm(parm);
        String jsonBody = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/callpu")
                        // 关键：Content-Type 是表单类型，但 body 是 JSON 文本
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ReturnCd").value("0"));
    }

    @Test
    void applicationJsonContentType_stillWorks() throws Exception {
        when(custStatQueryApi.getCustNameFromMaster(eq("C001")))
                .thenReturn(java.util.Optional.empty());

        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setCustId("C001");
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("CASH_GETCUST_INFO");
        req.setParm(parm);

        mockMvc.perform(post("/api/callpu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                // 未查到客户 → 业务失败信封 ReturnCd=99，但已正常进入方法体（非 415）
                .andExpect(jsonPath("$.ReturnCd").value("99"));
    }
}
