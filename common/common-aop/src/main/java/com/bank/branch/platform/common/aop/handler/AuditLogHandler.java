package com.bank.branch.platform.common.aop.handler;

import com.bank.branch.platform.common.aop.event.AuditLogEvent;

/**
 * 审计日志处理器接口
 * 由 system-governance-center 实现具体存储
 */
public interface AuditLogHandler {
    void handle(AuditLogEvent event);
}
