package com.bank.branch.platform.common.aop.handler;

import com.bank.branch.platform.common.aop.event.AuditLogEvent;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class NoopAuditLogHandlerTest {
    @Test void shouldNotThrow() {
        NoopAuditLogHandler handler = new NoopAuditLogHandler();
        AuditLogEvent event = new AuditLogEvent("TEST", "UNIT", "1", null,
            "E001", "ORG001", Instant.now(), null, null, null, null, null);
        assertDoesNotThrow(() -> handler.handle(event));
    }
}
