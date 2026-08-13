package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.entity.PtOrgProfile;
import com.bank.branch.platform.common.aop.event.AuditLogEvent;
import com.bank.branch.platform.common.aop.handler.AuditLogHandler;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 机构画像和机构组高危变更的结构化审计测试。 */
@ExtendWith(MockitoExtension.class)
class OrgGroupAuditServiceTest {

    @Mock
    private AuditLogHandler auditLogHandler;

    private OrgGroupAuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new OrgGroupAuditService(auditLogHandler, new ObjectMapper());
        when(auditLogHandler.isPersistent()).thenReturn(true);
        DataScopeContext context = new DataScopeContext();
        context.setEmpId("E10001");
        context.setOrgCode("ORG001");
        DataScopeContext.set(context);
        MDC.put("traceId", "trace-org-audit-001");
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
        MDC.clear();
    }

    @Test
    void membersReplacePersistsBeforeAfterDeltaReasonOperatorAndTraceId() {
        auditService.membersReplaced(
                "G_PRIMARY", Set.of("ORG_OLD", "ORG_KEEP"), Set.of("ORG_KEEP", "ORG_NEW"),
                "经营机构范围调整");

        ArgumentCaptor<AuditLogEvent> captor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(auditLogHandler).handle(captor.capture());
        AuditLogEvent event = captor.getValue();
        assertThat(event.action()).isEqualTo("ORG_GROUP_MEMBER_CHANGE");
        assertThat(event.targetType()).isEqualTo("PT_ORG_GROUP_MEMBER");
        assertThat(event.targetId()).isEqualTo("G_PRIMARY");
        assertThat(event.before()).contains("ORG_OLD", "ORG_KEEP");
        assertThat(event.after()).contains("ORG_KEEP", "ORG_NEW");
        assertThat(event.addedItems()).contains("ORG_NEW");
        assertThat(event.removedItems()).contains("ORG_OLD");
        assertThat(event.reason()).isEqualTo("经营机构范围调整");
        assertThat(event.operatorEmpId()).isEqualTo("E10001");
        assertThat(event.traceId()).isEqualTo("trace-org-audit-001");
    }

    @Test
    void profileChangePersistsBeforeAndAfterSnapshots() {
        PtOrgProfile before = profile("ORG001", "LOCAL_BRANCH", "PRIMARY");
        PtOrgProfile after = profile("ORG001", "SECONDARY_BRANCH", "PRIMARY");

        auditService.profileChanged(before, after, "修订机构经营画像");

        ArgumentCaptor<AuditLogEvent> captor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(auditLogHandler).handle(captor.capture());
        AuditLogEvent event = captor.getValue();
        assertThat(event.before()).contains("LOCAL_BRANCH");
        assertThat(event.after()).contains("SECONDARY_BRANCH");
        assertThat(event.addedItems()).isNull();
        assertThat(event.removedItems()).isNull();
    }

    @Test
    void unavailablePersistentAuditHandlerRejectsHighRiskConfigurationChange() {
        when(auditLogHandler.isPersistent()).thenReturn(false);

        assertThatThrownBy(() -> auditService.membersReplaced(
                "G_PRIMARY", Set.of("ORG_OLD"), Set.of("ORG_NEW"), "经营机构范围调整"))
                .hasMessageContaining("审计");
    }

    private static PtOrgProfile profile(String code, String nature, String level) {
        PtOrgProfile profile = new PtOrgProfile();
        profile.setOrgCode(code);
        profile.setOrgNature(nature);
        profile.setOperatingLevel(level);
        profile.setStatus("ACTIVE");
        profile.setVersion(1);
        return profile;
    }
}
