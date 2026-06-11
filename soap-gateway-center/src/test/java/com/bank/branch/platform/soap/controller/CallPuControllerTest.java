package com.bank.branch.platform.soap.controller;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.performance.api.AllocApi;
import com.bank.branch.platform.performance.api.CustStatQueryApi;
import com.bank.branch.platform.performance.api.PerfApprovalCmdApi;
import com.bank.branch.platform.performance.api.PerfApprovalQueryApi;
import com.bank.branch.platform.performance.api.dto.AllocAdjustApprovalItemDTO;
import com.bank.branch.platform.performance.api.dto.AllocAdjustSubmitCmd;
import com.bank.branch.platform.soap.controller.dto.CallPuRequest;
import com.bank.branch.platform.soap.service.CallPuDispatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CallPuController 单元测试。
 *
 * <p>覆盖 callpu 分发协议：PERF_LIST（审批列表）/ PERF_SAVE（新增申请）/ CASH_GETCUST_INFO（客户号查名），
 * 校验中文/数字码到后端 enum 的翻译与统一 {@code {ReturnCd, RspMsg}} 信封。</p>
 */
@ExtendWith(MockitoExtension.class)
class CallPuControllerTest {

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

    /** UserDTO（username=工号/PT_USER.USERNAME，empId=USER_ID/PT_USER.USER_ID）。 */
    private static UserDTO user(String employeeNo, String userId) {
        UserDTO dto = new UserDTO();
        dto.setUsername(employeeNo);
        dto.setEmpId(userId);
        return dto;
    }

    @BeforeEach
    void setUp() {
        // 工号(USERNAME) → USER_ID 约定：X → U_X；lenient 以容忍未触达解析的用例
        lenient().when(userApi.getUsersByUsernames(anyList())).thenAnswer(inv -> {
            List<String> names = inv.getArgument(0);
            return names.stream().map(n -> user(n, "U_" + n)).toList();
        });
        CallPuDispatchService dispatchService = new CallPuDispatchService(
                perfApprovalQueryApi, perfApprovalCmdApi, custStatQueryApi, userApi, allocApi, dictApi);
        mockMvc = MockMvcBuilders.standaloneSetup(new CallPuController(dispatchService)).build();
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    // ===================== PERF_LIST =====================

    /** 构造一条 callpu PERF_LIST 请求体。 */
    private String perfListBody(String employeeNo) throws Exception {
        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo(employeeNo);
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_LIST");
        req.setParm(parm);
        return json(req);
    }

    @Test
    void perfList_success_returnsMappedItems() throws Exception {
        AllocAdjustApprovalItemDTO dto = AllocAdjustApprovalItemDTO.builder()
                .perfAdjustNo("PA_001")
                .applyFullname("张三")
                .custName("某某客户")
                .applyTime(LocalDateTime.of(2026, 5, 30, 14, 30, 0))
                .status("IN_APPROVAL")
                .category("TODO")
                .build();
        when(perfApprovalQueryApi.listAllocAdjustApprovals(eq("U_E001"), eq(null), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 100, 1L, List.of(dto)));

        mockMvc.perform(post("/api/callpu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(perfListBody("E001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ReturnCd").value("0"))
                .andExpect(jsonPath("$.RspMsg.perfs[0].perfAdjustNo").value("PA_001"))
                .andExpect(jsonPath("$.RspMsg.perfs[0].applyFullname").value("张三"))
                .andExpect(jsonPath("$.RspMsg.perfs[0].custName").value("某某客户"))
                .andExpect(jsonPath("$.RspMsg.perfs[0].applyTime").value("2026-05-30 14:30:00"))
                .andExpect(jsonPath("$.RspMsg.perfs[0].apprStatus").value("0"));
    }

    @Test
    void perfList_statusMapping_approvedAndRejected() throws Exception {
        AllocAdjustApprovalItemDTO approved = AllocAdjustApprovalItemDTO.builder()
                .perfAdjustNo("PA_A").status("APPROVED").category("DONE").build();
        AllocAdjustApprovalItemDTO rejected = AllocAdjustApprovalItemDTO.builder()
                .perfAdjustNo("PA_R").status("REJECTED").category("DONE").build();
        when(perfApprovalQueryApi.listAllocAdjustApprovals(eq("U_E001"), eq(null), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 100, 2L, List.of(approved, rejected)));

        mockMvc.perform(post("/api/callpu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(perfListBody("E001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ReturnCd").value("0"))
                .andExpect(jsonPath("$.RspMsg.perfs[0].apprStatus").value("1"))
                .andExpect(jsonPath("$.RspMsg.perfs[1].apprStatus").value("2"));
    }

    @Test
    void perfList_blankEmployeeNo_returnsFail() throws Exception {
        mockMvc.perform(post("/api/callpu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(perfListBody("")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ReturnCd").value("99"));

        verify(perfApprovalQueryApi, never()).listAllocAdjustApprovals(eq(""), any(), anyInt(), anyInt());
    }

    @Test
    void unknownRuleName_returnsFail() throws Exception {
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("NOT_A_RULE");

        mockMvc.perform(post("/api/callpu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ReturnCd").value("99"));
    }

    // ===================== CASH_GETCUST_INFO =====================

    private String cashGetCustBody(String custId) throws Exception {
        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setCustId(custId);
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("CASH_GETCUST_INFO");
        req.setParm(parm);
        return json(req);
    }

    @Test
    void cashGetCustInfo_success_returnsCustName() throws Exception {
        // 客户号查名走 perf XAN_M98_CUST_STAT_SHOW3：CUST_ID=C001 → CUST_NAME
        when(custStatQueryApi.getCustNameByCustId("C001")).thenReturn(Optional.of("某某有限公司"));

        mockMvc.perform(post("/api/callpu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cashGetCustBody("C001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ReturnCd").value("0"))
                .andExpect(jsonPath("$.RspMsg.custName").value("某某有限公司"));
    }

    @Test
    void cashGetCustInfo_notFound_returnsFail() throws Exception {
        when(custStatQueryApi.getCustNameByCustId("CX")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/callpu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cashGetCustBody("CX")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ReturnCd").value("99"));
    }

    // ===================== PERF_SAVE =====================

    private CallPuRequest.Allocater allocater(String username, String fullname, String ratio) {
        CallPuRequest.Allocater a = new CallPuRequest.Allocater();
        a.setUsername(username);
        a.setFullname(fullname);
        a.setRatio(ratio);
        return a;
    }

    @Test
    void perfSave_success_validatesAndSubmits() throws Exception {
        when(perfApprovalCmdApi.submitAllocAdjust(any())).thenReturn("AA999");
        when(dictApi.isValidDictValue("PERF_BIZ_KIND", "CORP_DEPOSIT")).thenReturn(true);

        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setCustId("C001");
        parm.setApplyType("1");   // 公司 → CORP
        parm.setApplyRule("1");   // 账号 → ACCOUNT
        parm.setIouNo("ACC123");
        parm.setBusinessType("CORP_DEPOSIT"); // 字典码直传
        parm.setAdjustExplain("调整理由");
        parm.setAllocaters(List.of(
                allocater("E100", "张三", "60"),
                allocater("E200", "李四", "40")));
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_SAVE");
        req.setParm(parm);

        mockMvc.perform(post("/api/callpu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ReturnCd").value("0"));

        ArgumentCaptor<AllocAdjustSubmitCmd> captor = ArgumentCaptor.forClass(AllocAdjustSubmitCmd.class);
        verify(perfApprovalCmdApi).submitAllocAdjust(captor.capture());
        AllocAdjustSubmitCmd cmd = captor.getValue();
        assertThat(cmd.getCustType()).isEqualTo("CORP");
        assertThat(cmd.getAllocDim()).isEqualTo("ACCOUNT");
        assertThat(cmd.getBizKind()).isEqualTo("CORP_DEPOSIT");
        assertThat(cmd.getCustId()).isEqualTo("C001");
        assertThat(cmd.getAccountNo()).isEqualTo("ACC123");
        assertThat(cmd.getReason()).isEqualTo("调整理由");
        // 关键回归：applicant 与 item.empId 均为解析后的 USER_ID，工号不得直达 perf
        assertThat(cmd.getApplicant()).isEqualTo("U_E001");
        assertThat(cmd.getItems()).hasSize(2);
        assertThat(cmd.getItems().get(0).getEmpId()).isEqualTo("U_E100");
        assertThat(cmd.getItems().get(0).getRatio()).isEqualByComparingTo(new BigDecimal("60"));
    }

    @Test
    void perfSave_invalidApplyType_returnsFail() throws Exception {
        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo("E001");
        parm.setCustId("C001");
        parm.setApplyType("9");   // 非法
        parm.setApplyRule("1");
        parm.setBusinessType("存款");
        parm.setAllocaters(List.of(allocater("E100", "张三", "60")));
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_SAVE");
        req.setParm(parm);

        mockMvc.perform(post("/api/callpu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ReturnCd").value("99"));

        verify(perfApprovalCmdApi, never()).submitAllocAdjust(any());
    }

    // ===================== PERF_RECALL =====================

    private String recallBody(String empId, String perfAdjustNo) throws Exception {
        CallPuRequest.Parm parm = new CallPuRequest.Parm();
        parm.setEmployeeNo(empId);
        parm.setPerfAdjustNo(perfAdjustNo);
        CallPuRequest req = new CallPuRequest();
        req.setRuleName("PERF_RECALL");
        req.setParm(parm);
        return json(req);
    }

    @Test
    void perfRecall_success_delegatesWithdraw() throws Exception {
        mockMvc.perform(post("/api/callpu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recallBody("E001", "AA001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ReturnCd").value("0"));

        verify(perfApprovalCmdApi).withdrawAllocAdjust("AA001", "U_E001", null);
    }

    @Test
    void perfRecall_blankPerfAdjustNo_returnsFail() throws Exception {
        mockMvc.perform(post("/api/callpu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recallBody("E001", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ReturnCd").value("99"));

        verify(perfApprovalCmdApi, never()).withdrawAllocAdjust(any(), any(), any());
    }

    @Test
    void perfRecall_serviceThrows_returnsFail() throws Exception {
        org.mockito.Mockito.doThrow(new RuntimeException("无权撤回他人申请"))
                .when(perfApprovalCmdApi).withdrawAllocAdjust(eq("AA002"), eq("U_E999"), any());

        mockMvc.perform(post("/api/callpu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recallBody("E999", "AA002")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ReturnCd").value("99"));
    }
}
