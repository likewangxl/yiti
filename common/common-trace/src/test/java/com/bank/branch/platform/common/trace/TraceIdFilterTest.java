package com.bank.branch.platform.common.trace;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void shouldGenerateTraceIdAndCleanAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        final String[] capturedTraceId = {null};

        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            capturedTraceId[0] = MDC.get("traceId");
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        assertNotNull(capturedTraceId[0], "traceId should be set during filter chain");
        assertEquals(16, capturedTraceId[0].length(), "traceId should be 16 chars");
        assertNull(MDC.get("traceId"), "traceId should be cleaned after request");
    }

    @Test
    void shouldReuseTraceIdFromHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "external12345678");
        MockHttpServletResponse response = new MockHttpServletResponse();
        final String[] capturedTraceId = {null};

        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            capturedTraceId[0] = MDC.get("traceId");
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        assertEquals("external12345678", capturedTraceId[0]);
    }

    @Test
    void shouldIgnoreBlankTraceIdHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        final String[] capturedTraceId = {null};

        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            capturedTraceId[0] = MDC.get("traceId");
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        assertNotNull(capturedTraceId[0]);
        assertEquals(16, capturedTraceId[0].length(), "blank header should trigger new traceId");
    }

    @Test
    void shouldCleanMdcEvenIfChainThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        doThrow(new RuntimeException("chain error")).when(chain).doFilter(request, response);

        try {
            filter.doFilter(request, response, chain);
        } catch (RuntimeException ignored) {
            // expected
        }

        assertNull(MDC.get("traceId"), "traceId should be cleaned even after exception");
    }
}
