package com.bank.branch.platform.governance.handler;

import com.bank.branch.platform.common.aop.event.AuditLogEvent;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.governance.service.AuditLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * GovAuditLogHandler 单元测试
 */
@ExtendWith(MockitoExtension.class)
class GovAuditLogHandlerTest {

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private GovAuditLogHandler govAuditLogHandler;

    @Test
    @DisplayName("handle: 将 AuditLogEvent 转换为 AuditLogCmd 并调用 service.log()")
    void handle_convertsEventToCmd_andCallsService() {
        // given
        AuditLogEvent event = new AuditLogEvent(
                "DELETE",           // action -> bizAction
                "SYS_CONFIG",       // resourceType -> bizType
                "/api/config/123",  // resourceId -> resourceUrl
                "BK-001",           // businessKey -> (mapped to reason or requestParams as needed)
                "E10001",           // operatorEmpId -> empId
                "ORG-001",          // operatorOrgId
                Instant.parse("2026-04-03T10:00:00Z"), // requestTime
                "192.168.1.100",    // ip -> ipAddress
                "Mozilla/5.0",      // userAgent
                "{\"key\":\"old\"}", // before -> requestParams
                "{\"key\":\"new\"}", // after
                "配置变更原因"        // reason
        );

        // when
        govAuditLogHandler.handle(event);

        // then
        ArgumentCaptor<AuditLogCmd> captor = ArgumentCaptor.forClass(AuditLogCmd.class);
        verify(auditLogService).log(captor.capture());

        AuditLogCmd cmd = captor.getValue();
        assertThat(cmd.getBizAction()).isEqualTo("DELETE");
        assertThat(cmd.getBizType()).isEqualTo("SYS_CONFIG");
        assertThat(cmd.getResourceUrl()).isEqualTo("/api/config/123");
        assertThat(cmd.getEmpId()).isEqualTo("E10001");
        assertThat(cmd.getIpAddress()).isEqualTo("192.168.1.100");
        assertThat(cmd.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(cmd.getReason()).isEqualTo("配置变更原因");
    }

    @Test
    @DisplayName("handle: service 抛出异常时不向上抛出，保证业务流程不中断")
    void handle_serviceThrows_doesNotRethrow() {
        // given
        AuditLogEvent event = new AuditLogEvent(
                "UPDATE", "CUSTOMER", "/api/customer/1", "BK-002",
                "E10002", "ORG-002", Instant.now(),
                "10.0.0.1", "curl/7.68", null, null, null
        );
        doThrow(new RuntimeException("DB连接失败")).when(auditLogService).log(any(AuditLogCmd.class));

        // when & then — 不应抛出异常
        assertThatCode(() -> govAuditLogHandler.handle(event))
                .doesNotThrowAnyException();
    }
}
