package com.bank.branch.platform.common.aop.event;

import java.time.Instant;

/**
 * 审计事件模型
 */
public record AuditLogEvent(
    String action, String resourceType, String resourceId, String businessKey,
    String operatorEmpId, String operatorOrgId, Instant requestTime,
    String ip, String userAgent, String before, String after, String reason
) {}
