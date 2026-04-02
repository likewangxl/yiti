package com.bank.branch.platform.common.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MethodTimingAspectTest {
    @Test void shouldProceedAndReturnResult() throws Throwable {
        MethodTimingAspect aspect = new MethodTimingAspect();
        aspect.setWarnThresholdMs(500);
        aspect.setErrorThresholdMs(5000);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(sig.toShortString()).thenReturn("TestService.doSomething()");
        when(pjp.proceed()).thenReturn("result");
        Object result = aspect.around(pjp);
        assertEquals("result", result);
        verify(pjp).proceed();
    }

    @Test void shouldNotThrowWhenProceedSucceeds() throws Throwable {
        MethodTimingAspect aspect = new MethodTimingAspect();
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(sig.toShortString()).thenReturn("Test.method()");
        when(pjp.proceed()).thenReturn(null);
        assertNull(aspect.around(pjp));
    }
}
