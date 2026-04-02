package com.bank.branch.platform.common.aop;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

/**
 * 方法耗时统计切面
 */
@Slf4j
@Aspect
@Setter
public class MethodTimingAspect {

    private long warnThresholdMs = 500;
    private long errorThresholdMs = 5000;

    @Pointcut("@within(org.springframework.stereotype.Service)")
    public void serviceMethod() {}

    @Around("serviceMethod()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return pjp.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            String method = pjp.getSignature().toShortString();
            if (elapsed >= errorThresholdMs) {
                log.error("慢方法: {} 耗时 {}ms (超过ERROR阈值 {}ms)", method, elapsed, errorThresholdMs);
            } else if (elapsed >= warnThresholdMs) {
                log.warn("慢方法: {} 耗时 {}ms (超过WARN阈值 {}ms)", method, elapsed, warnThresholdMs);
            }
        }
    }
}
