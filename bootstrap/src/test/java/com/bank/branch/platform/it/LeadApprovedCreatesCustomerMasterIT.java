package com.bank.branch.platform.it;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.enums.LeadOp;
import com.bank.branch.platform.customer.enums.LeadStatus;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.it.config.TestMockConfig;
import com.bank.branch.platform.it.config.TestSecurityConfig;
import com.bank.branch.platform.workflow.api.event.ProcessCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Option B.2 Phase 1 (a) — 最小可信集 IT，验证 LeadApproved 事件链触发 cust_master 创建。
 *
 * <p>覆盖事件链路（绕过 Flowable，但走真实 Spring 事件路径）：</p>
 * <pre>
 *   @Transactional 方法
 *     → eventPublisher.publishEvent(new ProcessCompletedEvent(APPROVED))   ← 事务内 publish
 *   ↓ 事务 commit
 *   AFTER_COMMIT 阶段触发：
 *     WorkflowCallbackListener.onProcessCompleted (REQUIRES_NEW + AFTER_COMMIT)
 *       → leadMapper.updateStatusById(leadId, "APPROVED")
 *       → eventPublisher.publishEvent(new LeadApprovedEvent)   ← 事务内 publish
 *   ↓ 内事务 commit
 *   AFTER_COMMIT 阶段触发：
 *     LeadApprovedListener.handle (@TransactionalEventListener AFTER_COMMIT)
 *       → CustMasterAssemblerService.assembleFromLead → masterMapper.insert
 * </pre>
 *
 * <p><strong>FU-6 重构（2026-04-27）</strong>：</p>
 * <ul>
 *   <li>之前直接调 {@code workflowCallbackListener.handleWorkflowCallback(leadId, true, ...)} 公开方法
 *       绕开真实事件路径假绿覆盖 P0 bug（reviewer §G-3）；</li>
 *   <li>Phase 2 (c) 已通过 {@code WorkflowCallbackEventChainBugIT} 验证真实事件路径，本 IT 也切到
 *       TxPublisher 模式真实模拟 Flowable {@code ProcessCompletedListener.notify()} 的事务内
 *       publishEvent 路径；</li>
 *   <li>切换后 dead code {@code handleWorkflowCallback} 才能在 FU-6 主修复 commit 中被删除。</li>
 * </ul>
 *
 * <p><strong>测试约束</strong>：</p>
 * <ul>
 *   <li>仅 mock {@link CurrentUserApi}（不影响事件链路）</li>
 *   <li>不 mock 任何 listener / service / mapper（这是测试目标，必须走真实链路）</li>
 *   <li>不依赖 BPMN / Flowable，仅依赖 Spring 事件机制 + WorkflowCallbackListener.onProcessCompleted</li>
 *   <li>cust_claim 链路不在本范围（属 ClaimService.claim 主动认领，独立次链路）</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestMockConfig.class, TestSecurityConfig.class, LeadApprovedCreatesCustomerMasterIT.TxPublisher.class})
@Sql(scripts = {
        "/customer-marketing-schema.sql",
        "/customer-marketing-data.sql"
})
class LeadApprovedCreatesCustomerMasterIT {

    private static final String OPERATOR_EMP_ID = "user001";
    private static final String OPERATOR_ORG_ID = "BJ_CY";

    @Autowired
    private CustLeadMapper leadMapper;

    @Autowired
    private CustMasterMapper masterMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TxPublisher txPublisher;

    /**
     * 仅 mock CurrentUserApi。
     * 事件链路本身不依赖 ThreadLocal 用户上下文（mapper 直接操作 DB），
     * 但 SpringBoot 启动时其他模块的 @PostConstruct / Listener 可能会触发 CurrentUserApi 调用，
     * 故 mock 默认值避免 NPE。
     */
    @MockBean
    private CurrentUserApi currentUserApi;

    @BeforeEach
    void setUp() {
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn(OPERATOR_EMP_ID);
        Mockito.when(currentUserApi.getCurrentOrgCode()).thenReturn(OPERATOR_ORG_ID);
        Mockito.when(currentUserApi.isSystemAdmin()).thenReturn(true);
    }

    @Test
    @DisplayName("Option B.2-a - LeadApproved 事件链触发 cust_master 创建 + cust_lead 状态变更")
    void leadApproved_shouldCreateCustomerMaster_andUpdateLeadStatus() {
        // ========== 准备：插入 1 条 IN_APPROVAL 状态的 CREATE 类型线索 ==========
        String leadId = "lead-b2-001";
        String leadNo = "LEAD_B2_001";
        String businessKey = "LEAD:" + leadId;
        String processInstanceId = "PI_B2_001";

        CustLead lead = new CustLead();
        lead.setId(leadId);
        lead.setLeadNo(leadNo);
        lead.setLeadOp(LeadOp.CREATE.getCode());
        lead.setVersionNo(1);
        lead.setIsLatest(1);
        lead.setCustName("Phase B.2 测试客户A");
        lead.setUnifiedCreditCode("91310000B2TEST001");
        lead.setContactPerson("张三");
        lead.setContactMobile("13800000001");
        lead.setIndustry("IT");
        lead.setGroupType("GROUP");
        lead.setCustomerType("ENTERPRISE");
        lead.setIsKeystone(1);
        lead.setEnterpriseType("PRIVATE");
        lead.setGroupName("Phase B.2 集团");
        lead.setIsAccountOpened(1);
        lead.setCustomerDesc("LeadApproved 事件链 IT");
        lead.setCreditAmount(new BigDecimal("1000000.00"));
        lead.setCreditExposureAmount(new BigDecimal("600000.00"));
        lead.setLeadSource("MANUAL");
        // 模拟"已提交审批"状态：在真实链路中 LeadService.submitForApproval 会将状态推进到 IN_APPROVAL
        lead.setLeadStatus(LeadStatus.IN_APPROVAL.getCode());
        lead.setOwnerOrgId(OPERATOR_ORG_ID);
        lead.setCreatedBy(OPERATOR_EMP_ID);
        lead.setBusinessKey(businessKey);
        LocalDateTime now = LocalDateTime.now();
        lead.setCreatedTime(now);
        lead.setUpdatedTime(now);
        lead.setDeleted(0);

        leadMapper.insert(lead);

        // 校验前置：cust_master 在事件触发前不应该有这条 leadId 关联记录
        long preCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cust_master WHERE lead_id = ?",
                Long.class,
                leadId
        );
        assertThat(preCount).isZero();

        // ========== 触发：在 @Transactional 方法内 publishEvent(ProcessCompletedEvent, APPROVED) ==========
        // 真实模拟 workflow-center ProcessCompletedListener.notify() 的事务内 publishEvent 行为
        txPublisher.publishProcessCompletedInTransaction(processInstanceId, businessKey, "APPROVED");

        // ========== 断言 1：cust_lead.lead_status 由 IN_APPROVAL 变为 APPROVED ==========
        // WorkflowCallbackListener.onProcessCompleted → handleApproved → leadMapper.updateStatusById
        CustLead approvedLead = leadMapper.selectById(leadId);
        assertThat(approvedLead).isNotNull();
        assertThat(approvedLead.getLeadStatus()).isEqualTo(LeadStatus.APPROVED.getCode());

        // ========== 断言 2：cust_master 表新增 1 条记录（来自 CustMasterAssemblerService.handleCreate） ==========
        // LeadApprovedListener.handle → CustMasterAssemblerService.assembleFromLead → masterMapper.insert
        List<CustMaster> matched = jdbcTemplate.query(
                "SELECT * FROM cust_master WHERE lead_id = ?",
                (rs, rowNum) -> {
                    CustMaster m = new CustMaster();
                    m.setId(rs.getString("id"));
                    m.setCustNo(rs.getString("cust_no"));
                    m.setCustName(rs.getString("cust_name"));
                    m.setUnifiedCreditCode(rs.getString("unified_credit_code"));
                    m.setOwnerOrgId(rs.getString("owner_org_id"));
                    m.setLeadId(rs.getString("lead_id"));
                    m.setStatus(rs.getString("status"));
                    m.setDeleted(rs.getInt("deleted"));
                    return m;
                },
                leadId
        );

        assertThat(matched).hasSize(1);
        CustMaster created = matched.get(0);
        assertThat(created.getLeadId()).isEqualTo(leadId);
        assertThat(created.getCustName()).isEqualTo("Phase B.2 测试客户A");
        assertThat(created.getUnifiedCreditCode()).isEqualTo("91310000B2TEST001");
        assertThat(created.getOwnerOrgId()).isEqualTo(OPERATOR_ORG_ID);
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        assertThat(created.getDeleted()).isZero();
        // custNo 由 CustMasterAssemblerService.generateCustNo() 自动生成，格式：CUST_{millis}_{rand4}
        assertThat(created.getCustNo()).startsWith("CUST_");
    }

    /**
     * 包装类：在 @Transactional 方法内 publishEvent(ProcessCompletedEvent)，
     * 真实模拟 workflow-center ProcessCompletedListener.notify() 的事务内 publish 行为。
     *
     * <p>之所以单独抽一个 @Component：@Transactional 走 Spring AOP 代理，
     * 必须从外部 bean 调用才能生效（self-invocation 会绕过代理）。</p>
     */
    @Component
    public static class TxPublisher {

        private final ApplicationEventPublisher eventPublisher;

        public TxPublisher(ApplicationEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
        }

        /**
         * 在事务内发布 ProcessCompletedEvent，模拟 Flowable 流程结束时
         * ProcessCompletedListener.notify() 的真实路径。
         *
         * @param processInstanceId 流程实例 ID
         * @param businessKey       业务键 LEAD:xxx
         * @param outcome           审批结果（APPROVED / REJECTED）
         */
        @Transactional(rollbackFor = Exception.class)
        public void publishProcessCompletedInTransaction(String processInstanceId, String businessKey, String outcome) {
            eventPublisher.publishEvent(new ProcessCompletedEvent(
                    processInstanceId,
                    businessKey,
                    outcome,
                    null
            ));
        }
    }
}
