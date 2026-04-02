package com.bank.branch.platform.common.aop;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.aop.event.AuditLogEvent;
import com.bank.branch.platform.common.aop.handler.AuditLogHandler;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import java.time.Instant;

/**
 * 高危动作审计切面
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogHandler handler;

    @Around("@annotation(com.bank.branch.platform.common.aop.annotation.AuditLog)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();

        try {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            AuditLog annotation = sig.getMethod().getAnnotation(AuditLog.class);

            String empId = null;
            String orgId = null;
            DataScopeContext ctx = DataScopeContext.current();
            if (ctx != null) {
                empId = ctx.getEmpId();
                orgId = ctx.getOrgCode();
            }

            AuditLogEvent event = new AuditLogEvent(
                annotation.action(), annotation.resourceType(),
                null, null,
                empId, orgId,
                Instant.now(), null, null, null, null, null
            );
            handler.handle(event);
        } catch (Exception e) {
            log.error("审计日志记录失败", e);
        }

        return result;
    }
}
