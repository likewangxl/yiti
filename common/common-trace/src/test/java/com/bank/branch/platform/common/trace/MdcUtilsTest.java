package com.bank.branch.platform.common.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import static org.junit.jupiter.api.Assertions.*;

class MdcUtilsTest {

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void putAndGetTraceId() {
        MdcUtils.putTraceId("abc123");
        assertEquals("abc123", MdcUtils.getTraceId());
    }

    @Test
    void removeTraceId() {
        MdcUtils.putTraceId("abc123");
        MdcUtils.removeTraceId();
        assertNull(MdcUtils.getTraceId());
    }

    @Test
    void traceIdKeyShouldBeConstant() {
        assertEquals("traceId", MdcUtils.TRACE_ID_KEY);
    }
}
