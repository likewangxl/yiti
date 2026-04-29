package com.bank.branch.platform.it;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.enums.LeadOp;
import com.bank.branch.platform.customer.enums.LeadStatus;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.it.config.TestMockConfig;
import com.bank.branch.platform.it.config.TestSecurityConfig;
import com.bank.branch.platform.workflow.api.event.ProcessCompletedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
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
 * FU-2 红 IT — WorkflowCallbackListener 异常未被兜底吞掉 bug 复现。
 *
 * <p><strong>Bug 链路</strong>（reviewer §B-2，Phase 2 (c) follow-up）：</p>
 * <pre>
 *   ProcessCompletedEvent 触发 → onProcessCompleted 内任一 mapper 抛异常
 *     → 异常冒泡到 Spring 内部 AFTER_COMMIT 调用栈
 *     → Spring 打印 ERROR "TransactionSynchronizationUtils.afterCompletion threw exception"
 *     → 影响 REQUIRES_NEW 内嵌事务回滚
 *     → 后续同一事件的其他 listener 不会被影响（Spring 已 try-catch 隔离），但日志被污染
 *   ✗ 异常面向 Spring 框架而非业务，告警维度不统一，定位难度增加
 * </pre>
 *
 * <p><strong>根本原因</strong>：onProcessCompleted 裸跑业务逻辑，无整体 try-catch。
 * bizapp.LoanWorkflowListener 已用 try { 全部业务 } catch (Exception e) { log.error 不重抛 }
 * pattern 防御 — 让异常以"业务自定义 ERROR 日志"形式出现而非"Spring 框架 ERROR 日志"。</p>
 *
 * <p><strong>预期</strong>：</p>
 * <ul>
 *   <li>当前未修复代码：日志中出现
 *       {@code TransactionSynchronizationUtils.afterCompletion threw exception}（Spring 框架 ERROR）；</li>
 *   <li>修复后（加整体 try-catch）：日志中**不再**出现 Spring 的 afterCompletion ERROR，
 *       而是出现 {@code [WorkflowCallbackListener] 处理流程完成事件异常} 自定义业务 ERROR；</li>
 *   <li>两种情况下 cust_lead.lead_status 都保持 IN_APPROVAL（嵌事务回滚 / 早返回）。</li>
 * </ul>
 *
 * <p><strong>设计选择</strong>：</p>
 * <ul>
 *   <li>使用 @SpyBean CustLeadMapper 让 selectById 抛 RuntimeException；</li>
 *   <li>用 logback ListAppender 抓 Spring TransactionSynchronizationUtils 与
 *       WorkflowCallbackListener 的 ERROR 日志做差异断言；</li>
 *   <li>红 IT 主断言：Spring afterCompletion ERROR 不应出现（fix 后才成立）。</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestMockConfig.class, TestSecurityConfig.class, WorkflowCallbackExceptionSwallowBugIT.TxPublisher.class})
@Sql(scripts = {
        "/customer-marketing-schema.sql",
        "/customer-marketing-data.sql"
})
class WorkflowCallbackExceptionSwallowBugIT {

    private static final String OPERATOR_EMP_ID = "user001";
    private static final String OPERATOR_ORG_ID = "BJ_CY";

    @SpyBean
    private CustLeadMapper leadMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TxPublisher txPublisher;

    @MockBean
    private CurrentUserApi currentUserApi;

    /** 抓 Spring 框架 TransactionSynchronizationUtils 的 ERROR 日志（fix 后应消失） */
    private ListAppender<ILoggingEvent> springTxAppender;
    private Logger springTxLogger;

    /** 抓 WorkflowCallbackListener 的 ERROR 日志（fix 后应出现统一业务 ERROR） */
    private ListAppender<ILoggingEvent> listenerAppender;
    private Logger listenerLogger;

    @BeforeEach
    void setUp() {
        Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn(OPERATOR_EMP_ID);
        Mockito.when(currentUserApi.getCurrentOrgCode()).thenReturn(OPERATOR_ORG_ID);
        Mockito.when(currentUserApi.isSystemAdmin()).thenReturn(true);

        // 挂上 logback ListAppender 抓 ERROR 日志
        springTxLogger = (Logger) LoggerFactory.getLogger(
                "org.springframework.transaction.support.TransactionSynchronizationUtils");
        springTxAppender = new ListAppender<>();
        springTxAppender.start();
        springTxLogger.addAppender(springTxAppender);

        listenerLogger = (Logger) LoggerFactory.getLogger(
                "com.bank.branch.platform.customer.listener.WorkflowCallbackListener");
        listenerAppender = new ListAppender<>();
        listenerAppender.start();
        listenerLogger.addAppender(listenerAppender);
    }

    @AfterEach
    void tearDown() {
        if (springTxLogger != null && springTxAppender != null) {
            springTxLogger.detachAppender(springTxAppender);
        }
        if (listenerLogger != null && listenerAppender != null) {
            listenerLogger.detachAppender(listenerAppender);
        }
    }

    @Test
    @DisplayName("FU-2 - listener 内 mapper 异常应被业务自身 try-catch 吞掉，不应冒泡到 Spring TransactionSynchronizationUtils")
    void mapperException_shouldBeSwallowedByListenerNotSpring() {
        // ========== 准备：插入 1 条 IN_APPROVAL 状态的 CREATE 类型线索 ==========
        String leadId = "lead-exception-001";
        String leadNo = "LEAD_EXC_001";
        String businessKey = "LEAD:" + leadId;
        String processInstanceId = "PI_EXC_001";

        CustLead lead = new CustLead();
        lead.setId(leadId);
        lead.setLeadNo(leadNo);
        lead.setLeadOp(LeadOp.CREATE.getCode());
        lead.setVersionNo(1);
        lead.setIsLatest(1);
        lead.setCustName("FU-2 异常吞掉测试客户");
        lead.setUnifiedCreditCode("91310000FU2EXC001");
        lead.setContactPerson("赵六");
        lead.setContactMobile("13800000004");
        lead.setIndustry("FINANCE");
        lead.setGroupType("GROUP");
        lead.setCustomerType("ENTERPRISE");
        lead.setIsKeystone(1);
        lead.setEnterpriseType("PRIVATE");
        lead.setGroupName("FU-2 异常吞掉集团");
        lead.setIsAccountOpened(1);
        lead.setCustomerDesc("FU-2 异常兜底红 IT");
        lead.setCreditAmount(new BigDecimal("4000000.00"));
        lead.setCreditExposureAmount(new BigDecimal("2000000.00"));
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

        // ========== 配置 spy：让 selectById("lead-exception-001") 抛 RuntimeException ==========
        Mockito.doThrow(new RuntimeException("simulated DB error"))
                .when(leadMapper).selectById(leadId);

        // ========== 触发：在 @Transactional 方法内 publishEvent(ProcessCompletedEvent, APPROVED) ==========
        // Spring 在 AFTER_COMMIT 阶段已对 listener 异常做 try-catch 隔离，外层调用方不会感知异常。
        // 但 fix 前 listener 自己不吞 → Spring 框架打 ERROR 日志（污染日志面）；
        // fix 后 listener 自己 try-catch → 仅业务自定义 ERROR 日志，框架日志干净。
        txPublisher.publishProcessCompletedInTransaction(processInstanceId, businessKey, "APPROVED");

        // ========== 断言 1（核心 bug）：Spring TransactionSynchronizationUtils 不应有 ERROR 日志 ==========
        // 当前未修复代码：listener 抛异常 → Spring 在 afterCompletion 调用栈打 ERROR
        //   "TransactionSynchronization.afterCompletion threw exception" → 此断言 fail
        // 修复后：listener 自己 try-catch 吞掉异常 → Spring 不打这条 ERROR
        List<ILoggingEvent> springErrors = springTxAppender.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .toList();
        assertThat(springErrors)
                .as("listener 应自身 try-catch 吞异常，不应让 Spring TransactionSynchronizationUtils 打 ERROR 日志")
                .isEmpty();

        // ========== 断言 2：listener 应有自定义业务 ERROR 日志（统一告警面） ==========
        List<ILoggingEvent> listenerErrors = listenerAppender.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .toList();
        assertThat(listenerErrors)
                .as("listener 应输出自定义业务 ERROR 日志，便于运维统一告警维度")
                .isNotEmpty();
        assertThat(listenerErrors.get(0).getFormattedMessage())
                .contains("处理流程完成事件异常");

        // ========== 断言 3：cust_lead.lead_status 保持 IN_APPROVAL（嵌事务回滚 / 早返回） ==========
        String actualStatus = jdbcTemplate.queryForObject(
                "SELECT lead_status FROM cust_lead WHERE id = ?",
                String.class,
                leadId
        );
        assertThat(actualStatus)
                .as("listener 抛错后 cust_lead 状态应保持 IN_APPROVAL")
                .isEqualTo(LeadStatus.IN_APPROVAL.getCode());
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
