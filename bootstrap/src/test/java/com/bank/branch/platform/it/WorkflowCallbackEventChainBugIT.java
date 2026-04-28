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
import com.bank.branch.platform.workflow.listener.ProcessCompletedListener;
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
 * P0 BUG 复现 IT — WorkflowCallbackListener 链路中断。
 *
 * <p><strong>Bug 链路</strong>（模拟生产真实路径）：</p>
 * <pre>
 *   @Transactional 方法
 *     → eventPublisher.publishEvent(new ProcessCompletedEvent(...))   ← 事务内 publish（模拟 Flowable 真实路径）
 *   ↓ 事务 commit
 *   AFTER_COMMIT 阶段触发：
 *     WorkflowCallbackListener.onProcessCompleted
 *       (@TransactionalEventListener AFTER_COMMIT, fallbackExecution=true)
 *       ← 此时事务已结束 → 在事务外执行
 *       → leadMapper.updateStatusById(leadId, "APPROVED")     ← 由 mybatis 自动开启临时事务（OK）
 *       → eventPublisher.publishEvent(new LeadApprovedEvent)  ← 在事务外 publish（核心 bug）
 *   ↓
 *   LeadApprovedListener.handle (@TransactionalEventListener AFTER_COMMIT) ← 无 fallbackExecution
 *     ← 事件发布时无活跃事务 → Spring 静默 skip
 *   ✗ CustMasterAssemblerService 永不调用
 *   ✗ cust_master 表永不写入
 * </pre>
 *
 * <p><strong>区别于 LeadApprovedCreatesCustomerMasterIT</strong>：</p>
 * <ul>
 *   <li>历史版本的 {@code LeadApprovedCreatesCustomerMasterIT} 通过 {@code handleWorkflowCallback} 公开方法 + TransactionTemplate
 *       绕过了 {@code onProcessCompleted} 事件路径，假绿覆盖 bug；
 *       <strong>注</strong>：FU-6（commit {@code 4efde7e}）已删除 {@code handleWorkflowCallback} dead code，
 *       该 IT 已改为 TxPublisher 真事件路径（与本 IT 对齐）；</li>
 *   <li>本 IT 通过 {@code eventPublisher.publishEvent(ProcessCompletedEvent)} 在 @Transactional
 *       方法内发布事件，真实模拟 Flowable {@code ProcessCompletedListener.notify()} 的事务内 publish 行为，
 *       触发 {@code WorkflowCallbackListener.onProcessCompleted} 的真实 bug 路径。</li>
 * </ul>
 *
 * <p><strong>预期</strong>：当前生产代码（{@code fallbackExecution=true}，无 {@code @Transactional}）下断言 fail，
 * cust_master 永不创建。修复后断言转绿。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestMockConfig.class, TestSecurityConfig.class, WorkflowCallbackEventChainBugIT.TxPublisher.class})
@Sql(scripts = {
        "/customer-marketing-schema.sql",
        "/customer-marketing-data.sql"
})
class WorkflowCallbackEventChainBugIT {

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

    @MockBean
    private CurrentUserApi currentUserApi;

    @BeforeEach
    void setUp() {
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn(OPERATOR_EMP_ID);
        Mockito.when(currentUserApi.getCurrentOrgCode()).thenReturn(OPERATOR_ORG_ID);
        Mockito.when(currentUserApi.isSystemAdmin()).thenReturn(true);
    }

    @Test
    @DisplayName("P0 bug - ProcessCompletedEvent 事务内发布时 cust_master 应正确创建（链路完整性）")
    void processCompletedEvent_publishedInTransaction_shouldCreateCustomerMaster() {
        // ========== 准备：插入 1 条 IN_APPROVAL 状态的 CREATE 类型线索 ==========
        String leadId = "lead-bug-001";
        String leadNo = "LEAD_BUG_001";
        String businessKey = "LEAD:" + leadId;
        String processInstanceId = "PI_BUG_001";

        CustLead lead = new CustLead();
        lead.setId(leadId);
        lead.setLeadNo(leadNo);
        lead.setLeadOp(LeadOp.CREATE.getCode());
        lead.setVersionNo(1);
        lead.setIsLatest(1);
        lead.setCustName("P0 BUG 复现客户");
        lead.setUnifiedCreditCode("91310000P0BUG0001");
        lead.setContactPerson("李四");
        lead.setContactMobile("13800000002");
        lead.setIndustry("FINANCE");
        lead.setGroupType("GROUP");
        lead.setCustomerType("ENTERPRISE");
        lead.setIsKeystone(1);
        lead.setEnterpriseType("PRIVATE");
        lead.setGroupName("P0 BUG 集团");
        lead.setIsAccountOpened(1);
        lead.setCustomerDesc("P0 bug 复现 IT");
        lead.setCreditAmount(new BigDecimal("2000000.00"));
        lead.setCreditExposureAmount(new BigDecimal("1200000.00"));
        lead.setLeadSource("MANUAL");
        lead.setLeadStatus(LeadStatus.IN_APPROVAL.getCode());
        lead.setOwnerOrgId(OPERATOR_ORG_ID);
        lead.setCreatedBy(OPERATOR_EMP_ID);
        lead.setBusinessKey(businessKey);
        LocalDateTime now = LocalDateTime.now();
        lead.setCreatedTime(now);
        lead.setUpdatedTime(now);
        lead.setDeleted(0);

        leadMapper.insert(lead);

        // 校验前置：cust_master 在事件触发前不应有这条 leadId 关联记录
        long preCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cust_master WHERE lead_id = ?",
                Long.class,
                leadId
        );
        assertThat(preCount).isZero();

        // ========== 触发：在 @Transactional 方法内 publishEvent(ProcessCompletedEvent) ==========
        // 这一步真实模拟 workflow-center ProcessCompletedListener.notify() 的行为：
        // notify() 是 Flowable ExecutionListener，在 Flowable 事务内执行，内部 publishEvent 也在事务内
        txPublisher.publishProcessCompletedInTransaction(processInstanceId, businessKey);

        // ========== 断言 1：cust_lead 状态应变为 APPROVED ==========
        // WorkflowCallbackListener.onProcessCompleted (AFTER_COMMIT, fallbackExecution=true)
        // 仍能完成 leadMapper.updateStatusById（mybatis 自动开启临时事务）
        CustLead approvedLead = leadMapper.selectById(leadId);
        assertThat(approvedLead).isNotNull();
        assertThat(approvedLead.getLeadStatus())
                .as("WorkflowCallbackListener.onProcessCompleted 应将 cust_lead 状态推进为 APPROVED")
                .isEqualTo(LeadStatus.APPROVED.getCode());

        // ========== 断言 2（核心 bug）：cust_master 应被 LeadApprovedListener 创建 ==========
        // 当前生产代码下：onProcessCompleted 在 AFTER_COMMIT 阶段事务外执行
        // → 内部 publishEvent(LeadApprovedEvent) 在事务外 publish
        // → LeadApprovedListener.handle (AFTER_COMMIT, 无 fallbackExecution) 静默 skip
        // → CustMasterAssemblerService 永不调用 → cust_master 永不写入
        // → 此断言 fail，正是 bug 复现
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

        assertThat(matched)
                .as("LeadApprovedListener 应被正确触发 → CustMasterAssemblerService 创建主档")
                .hasSize(1);
        CustMaster created = matched.get(0);
        assertThat(created.getLeadId()).isEqualTo(leadId);
        assertThat(created.getCustName()).isEqualTo("P0 BUG 复现客户");
        assertThat(created.getUnifiedCreditCode()).isEqualTo("91310000P0BUG0001");
        assertThat(created.getOwnerOrgId()).isEqualTo(OPERATOR_ORG_ID);
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        assertThat(created.getDeleted()).isZero();
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
         */
        @Transactional(rollbackFor = Exception.class)
        public void publishProcessCompletedInTransaction(String processInstanceId, String businessKey) {
            eventPublisher.publishEvent(new ProcessCompletedListener.ProcessCompletedEvent(
                    processInstanceId,
                    businessKey,
                    "APPROVED",
                    null
            ));
        }
    }
}
