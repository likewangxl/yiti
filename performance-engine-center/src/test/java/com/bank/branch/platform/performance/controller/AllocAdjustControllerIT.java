package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.performance.controller.dto.AllocAdjustCreateReqDTO;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustItem;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustItemMapper;
import com.bank.branch.platform.performance.support.PerformanceControllerTestBase;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AllocAdjustController IT (V1.2 Q2.4).
 *
 * <p>覆盖 4 个端点：
 * <ul>
 *   <li>POST /api/perf/alloc-adjust/create</li>
 *   <li>GET /api/perf/alloc-adjust/{id}</li>
 *   <li>GET /api/perf/alloc-adjust/list</li>
 *   <li>POST /api/perf/alloc-adjust/{id}/withdraw</li>
 * </ul>
 *
 * <p>注解约束：
 * <ul>
 *   <li>所有端点 {@code @BizAuth(PERF_CONFIG, <具体 action>)}</li>
 *   <li>create / withdraw 必须 {@code @AuditLog(reasonRequired=true)}</li>
 * </ul>
 */
class AllocAdjustControllerIT extends PerformanceControllerTestBase {

    private static final String CONTROLLER_FQCN =
            "com.bank.branch.platform.performance.controller.AllocAdjustController";

    @MockBean
    private CurrentUserApi currentUserApi;

    @MockBean
    private CustomerQueryApi customerQueryApi;

    @MockBean
    private WorkflowApi workflowApi;

    @Autowired
    private PerfAllocAdjustApplyMapper applyMapper;

    @Autowired
    private PerfAllocAdjustItemMapper itemMapper;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUpCurrentUser() {
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        Mockito.when(currentUserApi.getCurrentOrgCode()).thenReturn("HQ");
        Mockito.when(currentUserApi.getCurrentRoleCodes()).thenReturn(Set.of("R_ADMIN"));
        Mockito.when(currentUserApi.isSystemAdmin()).thenReturn(true);

        CustomerDTO cust = new CustomerDTO();
        cust.setId("TEST_AA_CUST_C1");
        Mockito.when(customerQueryApi.getCustomer(Mockito.anyString()))
                .thenReturn(Optional.of(cust));

        Mockito.when(workflowApi.startProcess(Mockito.any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_IT_0001", null, null));
    }

    private PerfAllocAdjustApply buildExisting(String idSuffix, String status, String bizKind) {
        PerfAllocAdjustApply a = new PerfAllocAdjustApply();
        a.setId("TEST_AA_IT_" + idSuffix);
        a.setApplyNo("AA_IT_" + idSuffix);
        a.setCustId("TEST_AA_CUST_" + idSuffix);
        a.setAllocDim("RULE");
        a.setBizKind(bizKind);
        a.setStatus(status);
        a.setBusinessKey("ALLOC_ADJUST:TEST_AA_IT_" + idSuffix);
        a.setProcessInstanceId("PI_IT_" + idSuffix);
        a.setOwnerOrgId("ORG_IT");
        a.setRemark("it fixture " + idSuffix);
        a.setCreatedBy("admin");
        a.setCreatedTime(LocalDateTime.now());
        a.setUpdatedBy("admin");
        a.setUpdatedTime(LocalDateTime.now());
        return a;
    }

    // ============= create =============

    @Test
    void create_happyPath_returns200_andPersists() throws Exception {
        AllocAdjustCreateReqDTO req = new AllocAdjustCreateReqDTO();
        req.setCustId("TEST_AA_CUST_C1");
        req.setAllocDim("RULE");
        req.setBizKind("CORP_LOAN");
        req.setOwnerOrgId("ORG_IT");
        req.setReason("IT 测试");
        AllocAdjustCreateReqDTO.Item it1 = new AllocAdjustCreateReqDTO.Item();
        it1.setEmpId("EMP_IT_1");
        it1.setRatio(new BigDecimal("60"));
        AllocAdjustCreateReqDTO.Item it2 = new AllocAdjustCreateReqDTO.Item();
        it2.setEmpId("EMP_IT_2");
        it2.setRatio(new BigDecimal("40"));
        req.setItems(Arrays.asList(it1, it2));

        mockMvc.perform(post("/api/perf/alloc-adjust/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("IN_APPROVAL"));
    }

    @Test
    void create_emptyItems_returnsValidationFailed() throws Exception {
        AllocAdjustCreateReqDTO req = new AllocAdjustCreateReqDTO();
        req.setCustId("TEST_AA_CUST_C1");
        req.setAllocDim("RULE");
        req.setBizKind("CORP_LOAN");
        req.setOwnerOrgId("ORG_IT");
        req.setItems(java.util.Collections.emptyList());

        mockMvc.perform(post("/api/perf/alloc-adjust/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-42200"));
    }

    // ============= getById =============

    @Test
    void getById_returns200_withItems() throws Exception {
        PerfAllocAdjustApply apply = buildExisting("GET_OK", "IN_APPROVAL", "CORP_LOAN");
        applyMapper.insert(apply);
        PerfAllocAdjustItem it = new PerfAllocAdjustItem();
        it.setId("TEST_AA_IT_GET_OK_I1");
        it.setApplyId(apply.getId());
        it.setEmpId("EMP_GET");
        it.setRatio(new BigDecimal("100"));
        itemMapper.batchInsert(java.util.List.of(it));

        mockMvc.perform(get("/api/perf/alloc-adjust/{id}", apply.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value(apply.getId()))
                .andExpect(jsonPath("$.data.applyNo").value(apply.getApplyNo()))
                .andExpect(jsonPath("$.data.status").value("IN_APPROVAL"))
                .andExpect(jsonPath("$.data.items[0].empId").value("EMP_GET"));
    }

    @Test
    void getById_notFound_returnsValidationFailed() throws Exception {
        mockMvc.perform(get("/api/perf/alloc-adjust/{id}", "NO_SUCH_APPLY_AA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-42200"));
    }

    // ============= list =============

    @Test
    void list_withStatusAndBizKindFilters_returnsPage() throws Exception {
        applyMapper.insert(buildExisting("LIST_A", "IN_APPROVAL", "CORP_LOAN"));
        applyMapper.insert(buildExisting("LIST_B", "APPROVED", "CORP_LOAN"));

        mockMvc.perform(get("/api/perf/alloc-adjust/list")
                        .param("status", "IN_APPROVAL")
                        .param("bizKind", "CORP_LOAN")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.page.pageNo").value(1));
    }

    // ============= withdraw =============

    @Test
    void withdraw_inApproval_transitionsToRejected() throws Exception {
        PerfAllocAdjustApply apply = buildExisting("WITHDRAW", "IN_APPROVAL", "CORP_LOAN");
        applyMapper.insert(apply);

        String reasonBody = "{\"reason\": \"取消申请\"}";
        mockMvc.perform(post("/api/perf/alloc-adjust/{id}/withdraw", apply.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reasonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        PerfAllocAdjustApply after = applyMapper.selectById(apply.getId());
        assertThat(after.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    void withdraw_notFound_returnsValidationFailed() throws Exception {
        mockMvc.perform(post("/api/perf/alloc-adjust/{id}/withdraw", "NO_SUCH_WD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"err\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERF-42200"));
    }

    // ============= 注解约束 =============

    @Test
    void create_hasBizAuthWriteAndAuditLogWithReason() throws Exception {
        Class<?> clazz = Class.forName(CONTROLLER_FQCN);
        Method m = java.util.Arrays.stream(clazz.getDeclaredMethods())
                .filter(x -> x.getName().equals("create"))
                .findFirst()
                .orElseThrow();
        BizAuth ba = m.getAnnotation(BizAuth.class);
        assertThat(ba).isNotNull();
        assertThat(ba.bizType()).isEqualTo(BizType.PERF_CONFIG);
        assertThat(ba.action()).isEqualTo(BizAction.WRITE);
        AuditLog al = m.getAnnotation(AuditLog.class);
        assertThat(al).isNotNull();
        assertThat(al.reasonRequired()).isTrue();
    }

    @Test
    void withdraw_hasBizAuthAndAuditLogWithReason() throws Exception {
        Class<?> clazz = Class.forName(CONTROLLER_FQCN);
        Method m = java.util.Arrays.stream(clazz.getDeclaredMethods())
                .filter(x -> x.getName().equals("withdraw"))
                .findFirst()
                .orElseThrow();
        BizAuth ba = m.getAnnotation(BizAuth.class);
        assertThat(ba).isNotNull();
        assertThat(ba.bizType()).isEqualTo(BizType.PERF_CONFIG);
        AuditLog al = m.getAnnotation(AuditLog.class);
        assertThat(al).isNotNull();
        assertThat(al.reasonRequired()).isTrue();
    }

    @Test
    void getById_hasBizAuthRead() throws Exception {
        Class<?> clazz = Class.forName(CONTROLLER_FQCN);
        Method m = java.util.Arrays.stream(clazz.getDeclaredMethods())
                .filter(x -> x.getName().equals("getById"))
                .findFirst()
                .orElseThrow();
        BizAuth ba = m.getAnnotation(BizAuth.class);
        assertThat(ba).isNotNull();
        assertThat(ba.bizType()).isEqualTo(BizType.PERF_CONFIG);
        assertThat(ba.action()).isEqualTo(BizAction.READ);
    }
}
