package com.bank.branch.platform.common.aop.handler;

import com.bank.branch.platform.common.aop.event.AuditLogEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认审计日志处理器，仅日志输出
 */
@Slf4j
public class NoopAuditLogHandler implements AuditLogHandler {
    @Override
    public void handle(AuditLogEvent event) {
        log.info("审计日志(noop): action={}, resourceType={}, resourceId={}, operator={}",
            event.action(), event.resourceType(), event.resourceId(), event.operatorEmpId());
    }
}
