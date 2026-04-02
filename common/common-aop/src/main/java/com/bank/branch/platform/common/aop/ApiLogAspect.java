package com.bank.branch.platform.common.aop;

import com.bank.branch.platform.common.trace.MdcUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

/**
 * 接口入参/出参日志切面
 */
@Slf4j
@Aspect
public class ApiLogAspect {

    @Pointcut("@within(org.springframework.web.bind.annotation.RestController)")
    public void restController() {}

    @Around("restController()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().toShortString();
        String traceId = MdcUtils.getTraceId();
        log.info("[{}] >>> {} args={}", traceId, method, pjp.getArgs());
        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            long elapsed = System.currentTimeMillis() - start;
            log.info("[{}] <<< {} result={} ({}ms)", traceId, method, result, elapsed);
            return result;
        } catch (Throwable ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[{}] !!! {} error={} ({}ms)", traceId, method, ex.getMessage(), elapsed);
            throw ex;
        }
    }
}
