package com.bank.branch.platform.common.aop.event;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class AuditLogEventTest {
    @Test void shouldHoldAllFields() {
        Instant now = Instant.now();
        AuditLogEvent event = new AuditLogEvent("DELETE", "CUSTOMER", "C001", "BK001",
            "E001", "ORG001", now, "127.0.0.1", "Mozilla/5.0", "{}", "{}", "\u5ba2\u6237\u8fc1\u79fb");
        assertEquals("DELETE", event.action());
        assertEquals("C001", event.resourceId());
        assertEquals("E001", event.operatorEmpId());
        assertEquals(now, event.requestTime());
    }
}
