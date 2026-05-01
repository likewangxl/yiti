package com.bank.branch.platform.it;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.enums.LeadOp;
import com.bank.branch.platform.customer.enums.LeadStatus;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FU-1 红 IT — WorkflowCallbackListener 重复触发导致 cust_master 重复创建 bug 复现。
 *
 * <p><strong>Bug 链路</strong>（reviewer §B-1，Phase 2 (c) follow-up）：</p>
 * <pre>
 *   ProcessCompletedEvent 在事务内被 publish 两次（多实例 / 网络抖动 / 重发场景）
 *     → onProcessCompleted 触发两次（AFTER_COMMIT 阶段）
 *     → handleApproved 第一次：updateStatusById(IN_APPROVAL → APPROVED) + publishEvent(LeadApprovedEvent)
 *       → LeadApprovedListener.handle → CustMasterAssemblerService.handleCreate → cust_master INSERT (1)
 *     → handleApproved 第二次：updateStatusById（无条件，已经是 APPROVED 状态再写一次也无影响）
 *       但仍会 publishEvent(LeadApprovedEvent) 第二次！
 *       → LeadApprovedListener.handle 再触发一次 → cust_master INSERT (2)
 *   ✗ cust_master 表中 lead_id 等于 leadId 的行数 = 2（重复）
 * </pre>
 *
 * <p><strong>根本原因</strong>：handleApproved/handleRejected 用无条件 updateStatusById，
 * 缺乏类似 bizapp.LoanWorkflowListener 的 conditionalUpdateStatus 幂等保护。
 * 重复事件 → 重复 publishEvent → 重复 cust_master 创建。</p>
 *
 * <p><strong>预期</strong>：</p>
 * <ul>
 *   <li>当前未修复代码下：cust_master 行数 = 2，断言 hasSize(1) fail；</li>
 *   <li>修复后（加 conditionalUpdateStatus 幂等保护）：第二次 conditionalUpdateStatus(IN_APPROVAL → APPROVED)
 *       因状态已是 APPROVED 不匹配 → 返回 0 → 早返回 → 不再 publish LeadApprovedEvent → cust_master 不重复。</li>
 * </ul>
 *
 * <p><strong>设计选择</strong>：cust_master.lead_id 表设计中无 UNIQUE 约束，所以重复
 * 表现为 cust_master 出现 2 行而非 DuplicateKeyException 抛错。断言行数 = 1 是关键。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestMockConfig.class, TestSecurityConfig.class, WorkflowCallbackIdempotencyBugIT.TxPublisher.class})
@Sql(scripts = {
        "/customer-marketing-schema.sql",
        "/customer-marketing-data.sql"
})
class WorkflowCallbackIdempotencyBugIT {

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
    @DisplayName("FU-1 - ProcessCompletedEvent 重复 publish 时 cust_master 不应重复创建")
    void duplicateProcessCompletedEvent_shouldNotDuplicateCustomerMaster() {
        // ========== 准备：插入 1 条 IN_APPROVAL 状态的 CREATE 类型线索 ==========
        String leadId = "lead-idempotent-001";
        String leadNo = "LEAD_IDEM_001";
        String businessKey = "LEAD:" + leadId;
        String processInstanceId = "PI_IDEM_001";

        CustLead lead = new CustLead();
        lead.setId(leadId);
        lead.setLeadNo(leadNo);
        lead.setLeadOp(LeadOp.CREATE.getCode());
        lead.setVersionNo(1);
        lead.setIsLatest(1);
        lead.setCustName("FU-1 幂等测试客户");
        lead.setUnifiedCreditCode("91310000FU1IDEM001");
        lead.setContactPerson("王五");
        lead.setContactMobile("13800000003");
        lead.setIndustry("FINANCE");
        lead.setGroupType("GROUP");
        lead.setCustomerType("ENTERPRISE");
        lead.setIsKeystone(1);
        lead.setEnterpriseType("PRIVATE");
        lead.setGroupName("FU-1 幂等集团");
        lead.setIsAccountOpened(1);
        lead.setCustomerDesc("FU-1 幂等保护红 IT");
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

        // ========== 触发：模拟 ProcessCompletedEvent 被 publish 两次 ==========
        // 真实场景：多实例消费 / 重连 / 网络抖动重发等异常路径下可能发生
        // 第一次：状态推进 IN_APPROVAL → APPROVED + 发布 LeadApprovedEvent → cust_master 创建
        txPublisher.publishProcessCompletedInTransaction(processInstanceId, businessKey, "APPROVED");
        // 第二次：当前未修复代码下，handleApproved 仍会再次发布 LeadApprovedEvent → cust_master 重复创建
        txPublisher.publishProcessCompletedInTransaction(processInstanceId, businessKey, "APPROVED");

        // ========== 断言 1：cust_lead 状态为 APPROVED（不论是否幂等都应推进） ==========
        CustLead approvedLead = leadMapper.selectById(leadId);
        assertThat(approvedLead).isNotNull();
        assertThat(approvedLead.getLeadStatus()).isEqualTo(LeadStatus.APPROVED.getCode());

        // ========== 断言 2（核心 bug）：cust_master 行数应为 1（不重复） ==========
        // 当前未修复代码下：handleApproved 无幂等保护，重复 publishEvent → cust_master 创建 2 次 → fail
        // 修复后：conditionalUpdateStatus(IN_APPROVAL → APPROVED) 第二次因状态已变返回 0 → 早返回 → 不再 publish
        long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CUST_MASTER WHERE lead_id = ?",
                Long.class,
                leadId
        );
        assertThat(count)
                .as("ProcessCompletedEvent 重复触发时 cust_master 必须保持 1 行（幂等保护，FU-1）")
                .isEqualTo(1L);
    }

    /**
     * 包装类：在 @Transactional 方法内 publishEvent(ProcessCompletedEvent)，
     * 与 WorkflowCallbackEventChainBugIT.TxPublisher 同 pattern。
     */
    @Component
    public static class TxPublisher {

        private final ApplicationEventPublisher eventPublisher;

        public TxPublisher(ApplicationEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
        }

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
