package com.bank.branch.platform.common.aop.handler;

import com.bank.branch.platform.common.aop.event.AuditLogEvent;

/**
 * 审计日志处理器接口
 * 由 system-governance-center 实现具体存储
 */
public interface AuditLogHandler {

    /**
     * 处理器是否真正持久化审计记录。
     *
     * <p>普通切面审计允许降级为日志输出；高危配置写操作必须先确认此能力为 true，
     * 否则拒绝变更，避免留下不可追溯的权限配置。</p>
     */
    default boolean isPersistent() {
        return false;
    }

    /** 持久化或输出一条审计事件。 */
    void handle(AuditLogEvent event);
}
