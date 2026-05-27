package com.bank.branch.platform.common.aop.event;

import java.time.Instant;

/**
 * 审计事件模型
 *
 * <p>V1.6 扩展：补齐 bizType / requestUrl / requestMethod / requestParams / executionTime /
 * traceId / responseStatus / errorMsg 8 个字段，避免审计落库时 80% 字段为 NULL。</p>
 *
 * <p>构造时所有新字段允许传 null（向后兼容旧调用点）。</p>
 */
public record AuditLogEvent(
    String action, String resourceType, String resourceId, String businessKey,
    String operatorEmpId, String operatorOrgId, Instant requestTime,
    String ip, String userAgent, String before, String after, String reason,
    // —— V1.6 扩展字段 ——
    String bizType, String requestUrl, String requestMethod, String requestParams,
    Long executionTime, String traceId, Integer responseStatus, String errorMsg
) {
    /** 兼容旧 ctor（12 参数）：新字段全部 null。 */
    public AuditLogEvent(String action, String resourceType, String resourceId, String businessKey,
                         String operatorEmpId, String operatorOrgId, Instant requestTime,
                         String ip, String userAgent, String before, String after, String reason) {
        this(action, resourceType, resourceId, businessKey, operatorEmpId, operatorOrgId, requestTime,
             ip, userAgent, before, after, reason,
             null, null, null, null, null, null, null, null);
    }
}
