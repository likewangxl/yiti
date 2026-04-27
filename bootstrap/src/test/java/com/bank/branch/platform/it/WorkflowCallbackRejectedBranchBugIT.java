package com.bank.branch.platform.it;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.enums.LeadOp;
import com.bank.branch.platform.customer.enums.LeadStatus;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 (c) 红 IT — WorkflowCallbackListener 忽略 REJECTED outcome bug 复现。
 *
 * <p><strong>Bug 描述</strong>（对应 {@code WorkflowCallbackListener.java:87} TODO）：</p>
 * <pre>
 *   当前生产代码忽略 event.outcome()，无论 APPROVED/REJECTED 都走 handleApproved 路径：
 *     → cust_lead.status 被错误地更新为 APPROVED（应为 REJECTED）
 *     → CREATE/UPDATE 类型：错误发布 LeadApprovedEvent → 创建 cust_master（不应创建）
 *     → DELETE 类型：错误发布 LeadDeletedEvent → 失效 cust_master（不应失效）
 * </pre>
 *
 * <p><strong>本 IT 验证策略</strong>：</p>
 * <ol>
 *   <li>插入 1 条 IN_APPROVAL 状态的 CREATE 类型线索</li>
 *   <li>通过 {@link TxPublisher} 在 @Transactional 方法内
 *       publishEvent(ProcessCompletedEvent("REJECTED", ...))，模拟
 *       workflow-center {@code ProcessCompletedListener.notify()} 在 Flowable 事务内的真实事件路径</li>
 *   <li>断言 1: cust_lead.leadStatus == REJECTED（当前 bug 下为 APPROVED → fail）</li>
 *   <li>断言 2: cust_master 表对应 leadId 记录数为 0（当前 bug 下为 1 → fail）</li>
 * </ol>
 *
 * <p><strong>预期</strong>：当前生产代码下断言 fail，正是 bug 复现，等待 Phase 2 (c)
 * fix commit 在 onProcessCompleted 内按 outcome 分发后转绿。</p>
 *
 * <p><strong>与 {@link WorkflowCallbackEventChainBugIT} 区别</strong>：</p>
 * <ul>
 *   <li>P0 IT 验证 APPROVED 路径下 cust_master 是否被创建（链路完整性 / 事务策略 bug）；</li>
 *   <li>本 IT 验证 REJECTED 路径下 cust_master 是否未被错误创建（语义层 / outcome 分发 bug），
 *       两者正交。</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestMockConfig.class, TestSecurityConfig.class, WorkflowCallbackRejectedBranchBugIT.TxPublisher.class})
@Sql(scripts = {
        "/customer-marketing-schema.sql",
        "/customer-marketing-data.sql"
})
class WorkflowCallbackRejectedBranchBugIT {

    private static final String OPERATOR_EMP_ID = "user001";
    private static final String OPERATOR_ORG_ID = "BJ_CY";

    @Autowired
    private CustLeadMapper leadMapper;

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
    @DisplayName("REJECTED outcome 应将 cust_lead 推进 REJECTED 且不创建 cust_master")
    void processCompletedEvent_rejected_shouldNotCreateCustomerMasterAndShouldMarkRejected() {
        // ========== 准备：插入 1 条 IN_APPROVAL 状态的 CREATE 类型线索 ==========
        String leadId = "lead-rejected-001";
        String leadNo = "LEAD_REJECTED_001";
        String businessKey = "LEAD:" + leadId;
        String processInstanceId = "PI_REJECTED_001";
        String rejectReason = "测试驳回原因";

        CustLead lead = new CustLead();
        lead.setId(leadId);
        lead.setLeadNo(leadNo);
        lead.setLeadOp(LeadOp.CREATE.getCode());
        lead.setVersionNo(1);
        lead.setIsLatest(1);
        lead.setCustName("REJECTED 分支复现客户");
        lead.setUnifiedCreditCode("91310000REJECT001");
        lead.setContactPerson("王五");
        lead.setContactMobile("13800000003");
        lead.setIndustry("FINANCE");
        lead.setGroupType("GROUP");
        lead.setCustomerType("ENTERPRISE");
        lead.setIsKeystone(1);
        lead.setEnterpriseType("PRIVATE");
        lead.setGroupName("REJECTED 复现集团");
        lead.setIsAccountOpened(1);
        lead.setCustomerDesc("Phase 2 (c) REJECTED 分支复现 IT");
        lead.setCreditAmount(new BigDecimal("3000000.00"));
        lead.setCreditExposureAmount(new BigDecimal("1500000.00"));
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

        // 校验前置：cust_master 在事件触发前应无该 leadId 关联记录
        long preCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cust_master WHERE lead_id = ?",
                Long.class,
                leadId
        );
        assertThat(preCount).isZero();

        // ========== 触发：在 @Transactional 方法内 publishEvent(ProcessCompletedEvent, outcome=REJECTED) ==========
        // 模拟 workflow-center ProcessCompletedListener.notify() 在 Flowable 事务内的真实路径，
        // 但此次 outcome 为 REJECTED（流程变量 approved=false 时由 ProcessCompletedListener 转换）
        txPublisher.publishProcessCompletedInTransaction(processInstanceId, businessKey, rejectReason);

        // ========== 断言 1：cust_lead 状态应变为 REJECTED ==========
        // 当前生产代码下：onProcessCompleted 忽略 outcome，仍走 handleApproved 把 lead.status 标 APPROVED
        // → 此断言 fail，正是 bug 复现
        // 修复后：按 outcome 分发，REJECTED 路径下 lead.status = REJECTED
        CustLead approvedLead = leadMapper.selectById(leadId);
        assertThat(approvedLead).isNotNull();
        assertThat(approvedLead.getLeadStatus())
                .as("REJECTED outcome 时 cust_lead 状态应推进为 REJECTED（当前 bug 下被错误标为 APPROVED）")
                .isEqualTo(LeadStatus.REJECTED.getCode());

        // ========== 断言 2（核心 bug）：cust_master 应不被创建 ==========
        // 当前生产代码下：onProcessCompleted 忽略 outcome，仍走 handleApproved
        // → 发布 LeadApprovedEvent → CustMasterAssemblerService 创建 cust_master
        // → 此断言 fail（实际为 1，期望为 0），正是 bug 复现
        // 修复后：REJECTED 路径不发布 LeadApprovedEvent → cust_master 永不创建
        long postCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cust_master WHERE lead_id = ?",
                Long.class,
                leadId
        );
        assertThat(postCount)
                .as("REJECTED outcome 时不应创建 cust_master（当前 bug 下错误地创建了 1 条）")
                .isZero();
    }

    /**
     * 包装类：在 @Transactional 方法内 publishEvent(ProcessCompletedEvent)，
     * 真实模拟 workflow-center ProcessCompletedListener.notify() 在 Flowable 事务内的 publish 行为。
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
         * 在事务内发布 ProcessCompletedEvent，outcome 固定为 REJECTED，
         * 模拟 ProcessCompletedListener.notify() 在流程变量 approved=false 时的真实路径。
         *
         * @param processInstanceId 流程实例 ID
         * @param businessKey       业务键 LEAD:xxx
         * @param reason            驳回原因（携带在事件 reason 字段）
         */
        @Transactional(rollbackFor = Exception.class)
        public void publishProcessCompletedInTransaction(String processInstanceId, String businessKey, String reason) {
            eventPublisher.publishEvent(new ProcessCompletedListener.ProcessCompletedEvent(
                    processInstanceId,
                    businessKey,
                    "REJECTED",
                    reason
            ));
        }
    }
}
