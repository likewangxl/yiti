package com.bank.branch.platform.common.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class TraceIdInterceptorTest {

    private final TraceIdInterceptor interceptor = new TraceIdInterceptor();

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void shouldSetTraceIdInResponseHeader() {
        MdcUtils.putTraceId("test123");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        assertEquals("test123", response.getHeader("X-Trace-Id"));
    }

    @Test
    void shouldNotSetHeaderWhenNoTraceId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        assertNull(response.getHeader("X-Trace-Id"));
    }
}
