package com.bank.branch.platform.it;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.enums.LeadOp;
import com.bank.branch.platform.customer.enums.LeadStatus;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.customer.service.LeadCallbackCompensationService;
import com.bank.branch.platform.it.config.TestMockConfig;
import com.bank.branch.platform.it.config.TestSecurityConfig;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.BizProcessMapDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FU-14 红 IT —— 孤儿 lead 数据补偿机制（{@link LeadCallbackCompensationService}）
 * 的 3 case 端到端验证。
 *
 * <p><strong>背景</strong>：Phase 2.5 FU-2 给
 * {@code WorkflowCallbackListener.onProcessCompleted} 加了整体 try-catch 兜底
 * （吞异常避免冒泡到 Spring AFTER_COMMIT 链）。副作用：listener 异常被自吞后，
 * {@code cust_lead.lead_status} 可能停留在 IN_APPROVAL，但 Flowable 流程实际
 * 已 COMPLETED，形成孤儿数据。FU-14 引入定时补偿机制，巡检 stuck IN_APPROVAL
 * leads，通过 {@link WorkflowApi} 反查流程真实状态自动推进至 APPROVED/REJECTED。</p>
 *
 * <p><strong>测试策略</strong>：</p>
 * <ul>
 *   <li>{@code @MockBean WorkflowApi} —— 控制反查行为，模拟流程已 COMPLETED 且
 *       outcome 为 APPROVED / REJECTED / 流程仍 RUNNING 三种场景；</li>
 *   <li>直接 SQL 插入 stuck lead（updated_time = 20 分钟前），
 *       超过默认 {@code customer.lead-compensation.stuck-threshold-minutes=10}；</li>
 *   <li>调 {@code compensationService.scanAndCompensate()} 同步触发补偿；</li>
 *   <li>断言 lead.lead_status + cust_master 行数。</li>
 * </ul>
 *
 * <p><strong>3 case</strong>：</p>
 * <ol>
 *   <li>case 1 APPROVED：mock outcome="APPROVED" → 期望 lead → APPROVED + cust_master 1 行</li>
 *   <li>case 2 REJECTED：mock outcome="REJECTED" → 期望 lead → REJECTED + cust_master 0 行</li>
 *   <li>case 3 RUNNING：mock processStatus="RUNNING" → 期望 lead 仍 IN_APPROVAL（未推进）</li>
 * </ol>
 *
 * <p><strong>红 commit 期望</strong>：所有 case fail（compensationService 抛
 * {@link UnsupportedOperationException}），绿 commit 全部转绿。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestMockConfig.class, TestSecurityConfig.class})
@Sql(scripts = {
        "/customer-marketing-schema.sql",
        "/customer-marketing-data.sql"
})
class LeadCallbackCompensationIT {

    private static final String OPERATOR_EMP_ID = "user001";
    private static final String OPERATOR_ORG_ID = "BJ_CY";

    @Autowired
    private CustLeadMapper leadMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LeadCallbackCompensationService compensationService;

    /**
     * 仅 mock CurrentUserApi 防止其他模块启动钩子触发 NPE。
     */
    @MockBean
    private CurrentUserApi currentUserApi;

    /**
     * Mock WorkflowApi —— 补偿 Service 通过它反查流程真实状态。
     * <p>仅本 IT 使用 compensationService.scanAndCompensate，不会触发其它业务路径
     * 调用 WorkflowApi.startProcess 等方法，故全 mock 无副作用。</p>
     */
    @MockBean
    private WorkflowApi workflowApi;

    @BeforeEach
    void setUp() {
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn(OPERATOR_EMP_ID);
        Mockito.when(currentUserApi.getCurrentOrgCode()).thenReturn(OPERATOR_ORG_ID);
        Mockito.when(currentUserApi.isSystemAdmin()).thenReturn(true);
    }

    @Test
    @DisplayName("FU-14 case 1 - 流程已 COMPLETED + APPROVED → 补偿推进 lead → APPROVED 并创建 cust_master")
    void compensation_picksUp_stuckApprovedLead() {
        String leadId = "lead-comp-001";
        String processInstanceId = "PI_COMP_001";
        insertStuckLead(leadId, "LEAD_COMP_001", "FU-14 补偿测试客户A", processInstanceId);

        // mock workflowApi：流程已 COMPLETED + outcome=APPROVED
        BizProcessMapDTO completedDto = new BizProcessMapDTO();
        completedDto.setProcessInstanceId(processInstanceId);
        completedDto.setProcessStatus("COMPLETED");
        completedDto.setBusinessKey("LEAD:" + leadId);
        Mockito.when(workflowApi.getProcessByBizTypeAndBizId("LEAD", leadId)).thenReturn(completedDto);
        Mockito.when(workflowApi.getProcessOutcome(processInstanceId)).thenReturn(Optional.of("APPROVED"));

        // 触发补偿
        compensationService.scanAndCompensate();

        // 断言 1：lead 推进到 APPROVED
        CustLead recovered = leadMapper.selectById(leadId);
        assertThat(recovered).isNotNull();
        assertThat(recovered.getLeadStatus())
                .as("APPROVED outcome 必须将 stuck lead 推进到 APPROVED")
                .isEqualTo(LeadStatus.APPROVED.getCode());

        // 断言 2：cust_master 应有 1 行（来自 LeadApprovedListener → CustMasterAssemblerService）
        long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CUST_MASTER WHERE lead_id = ?",
                Long.class,
                leadId
        );
        assertThat(count)
                .as("APPROVED 推进后必须触发 LeadApprovedEvent 链路创建 cust_master")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("FU-14 case 2 - 流程已 COMPLETED + REJECTED → 补偿推进 lead → REJECTED 不创建 cust_master")
    void compensation_picksUp_stuckRejectedLead() {
        String leadId = "lead-comp-002";
        String processInstanceId = "PI_COMP_002";
        insertStuckLead(leadId, "LEAD_COMP_002", "FU-14 补偿测试客户B", processInstanceId);

        BizProcessMapDTO completedDto = new BizProcessMapDTO();
        completedDto.setProcessInstanceId(processInstanceId);
        completedDto.setProcessStatus("COMPLETED");
        completedDto.setBusinessKey("LEAD:" + leadId);
        Mockito.when(workflowApi.getProcessByBizTypeAndBizId("LEAD", leadId)).thenReturn(completedDto);
        Mockito.when(workflowApi.getProcessOutcome(processInstanceId)).thenReturn(Optional.of("REJECTED"));

        compensationService.scanAndCompensate();

        // 断言 1：lead 推进到 REJECTED
        CustLead recovered = leadMapper.selectById(leadId);
        assertThat(recovered).isNotNull();
        assertThat(recovered.getLeadStatus())
                .as("REJECTED outcome 必须将 stuck lead 推进到 REJECTED")
                .isEqualTo(LeadStatus.REJECTED.getCode());

        // 断言 2：cust_master 应为 0 行（驳回路径不创建）
        long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CUST_MASTER WHERE lead_id = ?",
                Long.class,
                leadId
        );
        assertThat(count)
                .as("REJECTED 路径不应触发 cust_master 创建")
                .isZero();
    }

    @Test
    @DisplayName("FU-14 case 3 - 流程仍 RUNNING → 补偿不推进 lead（保持 IN_APPROVAL）")
    void compensation_skipsRunningProcess() {
        String leadId = "lead-comp-003";
        String processInstanceId = "PI_COMP_003";
        insertStuckLead(leadId, "LEAD_COMP_003", "FU-14 补偿测试客户C", processInstanceId);

        // mock 流程仍 RUNNING
        BizProcessMapDTO runningDto = new BizProcessMapDTO();
        runningDto.setProcessInstanceId(processInstanceId);
        runningDto.setProcessStatus("RUNNING");
        runningDto.setBusinessKey("LEAD:" + leadId);
        Mockito.when(workflowApi.getProcessByBizTypeAndBizId("LEAD", leadId)).thenReturn(runningDto);

        compensationService.scanAndCompensate();

        // 断言：lead 状态保持 IN_APPROVAL（流程仍在跑，补偿应跳过）
        CustLead recovered = leadMapper.selectById(leadId);
        assertThat(recovered).isNotNull();
        assertThat(recovered.getLeadStatus())
                .as("流程仍 RUNNING 时补偿必须跳过，lead 保持 IN_APPROVAL")
                .isEqualTo(LeadStatus.IN_APPROVAL.getCode());

        // cust_master 也应保持 0 行
        long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CUST_MASTER WHERE lead_id = ?",
                Long.class,
                leadId
        );
        assertThat(count).isZero();

        // 同时验证：getProcessOutcome 不应该被调用（因为流程未完成）
        Mockito.verify(workflowApi, Mockito.never()).getProcessOutcome(Mockito.anyString());
    }

    /**
     * 插入一条 stuck IN_APPROVAL lead，updated_time 设为 20 分钟前
     * （超过默认 stuckThresholdMinutes=10）。
     */
    private void insertStuckLead(String leadId, String leadNo, String custName, String processInstanceId) {
        CustLead lead = new CustLead();
        lead.setId(leadId);
        lead.setLeadNo(leadNo);
        lead.setLeadOp(LeadOp.CREATE.getCode());
        lead.setVersionNo(1);
        lead.setIsLatest(1);
        lead.setCustName(custName);
        lead.setUnifiedCreditCode("91310000FU14COMP" + leadId);
        lead.setContactPerson("补偿联系人");
        lead.setContactMobile("13800001111");
        lead.setIndustry("FINANCE");
        lead.setGroupType("GROUP");
        lead.setCustomerType("ENTERPRISE");
        lead.setIsKeystone(1);
        lead.setEnterpriseType("PRIVATE");
        lead.setGroupName("FU-14 集团");
        lead.setIsAccountOpened(1);
        lead.setCustomerDesc("FU-14 孤儿 lead 补偿 IT");
        lead.setCreditAmount(new BigDecimal("500000.00"));
        lead.setCreditExposureAmount(new BigDecimal("250000.00"));
        lead.setLeadSource("MANUAL");
        lead.setLeadStatus(LeadStatus.IN_APPROVAL.getCode());
        lead.setOwnerOrgId(OPERATOR_ORG_ID);
        lead.setCreatedBy(OPERATOR_EMP_ID);
        lead.setBusinessKey("LEAD:" + leadId);
        lead.setProcessInstanceId(processInstanceId);
        // 关键：updated_time 设为 20 分钟前，超过默认 stuckThresholdMinutes=10
        LocalDateTime stuckTime = LocalDateTime.now().minusMinutes(20);
        lead.setCreatedTime(stuckTime);
        lead.setUpdatedTime(stuckTime);
        lead.setDeleted(0);

        leadMapper.insert(lead);

        // 注意：mapper.insert 的 XML 写法 NOW() 会被覆盖，但 insert 走的是 #{updatedTime}
        // 应该已经写入 stuckTime；这里再做一次 jdbc 强制设置防御性兜底，确保 stuck 条件生效
        jdbcTemplate.update("UPDATE CUST_LEAD SET updated_time = ? WHERE id = ?", stuckTime, leadId);
    }
}
