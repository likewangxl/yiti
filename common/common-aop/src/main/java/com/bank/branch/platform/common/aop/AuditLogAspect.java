package com.bank.branch.platform.common.aop;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.aop.event.AuditLogEvent;
import com.bank.branch.platform.common.aop.handler.AuditLogHandler;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Instant;

/**
 * 高危动作审计切面（V1.6 增强）
 *
 * <p>采集字段从 V1.5 的 4 项（action/resourceType/empId/orgId）扩展到 12 项，
 * 增加 bizType（来自 @BizAuth）/ url+method+ip+UA（来自 HttpServletRequest）/
 * traceId（来自 MDC）/ executionTime / responseStatus / errorMsg / reason（反射 args）。</p>
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogHandler handler;

    @Around("@annotation(com.bank.branch.platform.common.aop.annotation.AuditLog)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        long t0 = System.currentTimeMillis();
        Throwable thrown = null;
        Object result = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable e) {
            thrown = e;
            throw e;
        } finally {
            try {
                writeAudit(pjp, t0, thrown);
            } catch (Exception e) {
                log.error("审计日志记录失败", e);
            }
        }
    }

    private void writeAudit(ProceedingJoinPoint pjp, long t0, Throwable thrown) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        AuditLog ann = method.getAnnotation(AuditLog.class);
        BizAuth bizAuthAnn = method.getAnnotation(BizAuth.class);
        String bizType = (bizAuthAnn != null && bizAuthAnn.bizType() != null) ? bizAuthAnn.bizType().getCode() : null;

        String empId = null, orgId = null;
        DataScopeContext ctx = DataScopeContext.current();
        if (ctx != null) { empId = ctx.getEmpId(); orgId = ctx.getOrgCode(); }

        // HttpRequest 上下文（同线程才有；批处理/定时任务场景下为 null）
        String url = null, httpMethod = null, ip = null, ua = null;
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            url = req.getRequestURI();
            httpMethod = req.getMethod();
            ip = extractIp(req);
            ua = req.getHeader("User-Agent");
        }
        String traceId = MDC.get("traceId");
        String reason = extractReason(pjp.getArgs());
        long cost = System.currentTimeMillis() - t0;
        Integer status = thrown != null ? 500 : 200;
        String errMsg = thrown != null ? thrown.getMessage() : null;

        AuditLogEvent event = new AuditLogEvent(
            ann.action(), ann.resourceType(),
            null, null,
            empId, orgId,
            Instant.now(),
            ip, ua, null, null, reason,
            bizType, url, httpMethod, null,
            cost, traceId, status, errMsg
        );
        handler.handle(event);
    }

    /** 从控制器参数里反射找 getReason() 字符串（支持 ChangeStatusReqDTO / ReleaseSlotReqDTO 等）。 */
    private String extractReason(Object[] args) {
        if (args == null) return null;
        for (Object a : args) {
            if (a == null) continue;
            try {
                Method m = a.getClass().getMethod("getReason");
                Object v = m.invoke(a);
                if (v != null && !v.toString().isBlank()) return v.toString();
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) { /* 反射失败不影响主流程 */ }
        }
        return null;
    }

    /** 从 X-Forwarded-For / X-Real-IP / RemoteAddr 三选一拿真实 IP。 */
    private String extractIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = req.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) return ip;
        return req.getRemoteAddr();
    }
}
