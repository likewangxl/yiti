package com.bank.branch.platform.common.aop;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.aop.event.AuditLogEvent;
import com.bank.branch.platform.common.aop.handler.AuditLogHandler;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
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

    @AuditLog(action = "TEST", resourceType = "UNIT")
    void annotatedMethod() {}
}
