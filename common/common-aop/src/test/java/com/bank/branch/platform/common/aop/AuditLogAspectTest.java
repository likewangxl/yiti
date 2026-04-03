package com.bank.branch.platform.common.aop;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.aop.event.AuditLogEvent;
import com.bank.branch.platform.common.aop.handler.AuditLogHandler;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AuditLogAspectTest {

    @Test void shouldDelegateToHandler() throws Throwable {
        AuditLogHandler handler = mock(AuditLogHandler.class);
        AuditLogAspect aspect = new AuditLogAspect(handler);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(sig.getMethod()).thenReturn(
            AuditLogAspectTest.class.getDeclaredMethod("annotatedMethod"));
        when(pjp.proceed()).thenReturn("ok");

        Object result = aspect.around(pjp);
        assertEquals("ok", result);
        verify(handler).handle(any(AuditLogEvent.class));
    }

    @Test void shouldProceedEvenIfHandlerFails() throws Throwable {
        AuditLogHandler handler = mock(AuditLogHandler.class);
        doThrow(new RuntimeException("handler error")).when(handler).handle(any());
        AuditLogAspect aspect = new AuditLogAspect(handler);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(sig.getMethod()).thenReturn(
            AuditLogAspectTest.class.getDeclaredMethod("annotatedMethod"));
        when(pjp.proceed()).thenReturn("ok");

        Object result = aspect.around(pjp);
        assertEquals("ok", result);
    }

    @AfterEach void cleanup() { DataScopeContext.clear(); }

    @Test void shouldCaptureActionAndResourceTypeFromAnnotation() throws Throwable {
        AuditLogHandler handler = mock(AuditLogHandler.class);
        AuditLogAspect aspect = new AuditLogAspect(handler);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(sig.getMethod()).thenReturn(
            AuditLogAspectTest.class.getDeclaredMethod("annotatedMethod"));
        when(pjp.proceed()).thenReturn("ok");

        aspect.around(pjp);

        ArgumentCaptor<AuditLogEvent> captor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(handler).handle(captor.capture());
        AuditLogEvent event = captor.getValue();
        assertEquals("TEST", event.action());
        assertEquals("UNIT", event.resourceType());
    }

    @Test void shouldCaptureEmpIdFromDataScopeContext() throws Throwable {
        DataScopeContext ctx = new DataScopeContext();
        ctx.setEmpId("E999");
        ctx.setOrgCode("ORG_TEST");
        DataScopeContext.set(ctx);

        AuditLogHandler handler = mock(AuditLogHandler.class);
        AuditLogAspect aspect = new AuditLogAspect(handler);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(sig.getMethod()).thenReturn(
            AuditLogAspectTest.class.getDeclaredMethod("annotatedMethod"));
        when(pjp.proceed()).thenReturn("ok");

        aspect.around(pjp);

        ArgumentCaptor<AuditLogEvent> captor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(handler).handle(captor.capture());
        AuditLogEvent event = captor.getValue();
        assertEquals("E999", event.operatorEmpId());
        assertEquals("ORG_TEST", event.operatorOrgId());
    }

    @Test void shouldHaveNullEmpIdWhenNoContext() throws Throwable {
        // DataScopeContext not set
        AuditLogHandler handler = mock(AuditLogHandler.class);
        AuditLogAspect aspect = new AuditLogAspect(handler);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(sig.getMethod()).thenReturn(
            AuditLogAspectTest.class.getDeclaredMethod("annotatedMethod"));
        when(pjp.proceed()).thenReturn("ok");

        aspect.around(pjp);

        ArgumentCaptor<AuditLogEvent> captor = ArgumentCaptor.forClass(AuditLogEvent.class);
        verify(handler).handle(captor.capture());
        assertNull(captor.getValue().operatorEmpId());
    }

    @AuditLog(action = "TEST", resourceType = "UNIT")
    void annotatedMethod() {}
}
