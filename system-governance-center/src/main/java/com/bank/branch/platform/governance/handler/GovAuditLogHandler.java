package com.bank.branch.platform.governance.handler;

import com.bank.branch.platform.common.aop.event.AuditLogEvent;
import com.bank.branch.platform.common.aop.handler.AuditLogHandler;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.governance.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 治理模块审计日志处理器
 *
 * <p>实现 common-aop 中的 AuditLogHandler 接口，将 @AuditLog 注解产生的审计事件
 * 路由到 system-governance-center 的 AuditLogService 进行持久化。</p>
 *
 * <p>V1.6 升级：透传 AuditLogEvent 新增的 8 个扩展字段（bizType / requestUrl /
 * requestMethod / executionTime / responseStatus / errorMsg / traceId / requestParams），
 * 不再把 resourceType 当成 bizType 错位写入。</p>
 *
 * <p>使用 @Primary 确保在 NoopAuditLogHandler 同时存在时优先使用本实现。普通
 * {@code @AuditLog} 调用仍由 common-aop 切面捕获异常并继续主流程；直接调用本处理器的
 * 高危配置服务会收到持久化异常，从而使其事务回滚，不能产生无审计记录的权限变更。</p>
 */
@Component
@Primary
@RequiredArgsConstructor
public class GovAuditLogHandler implements AuditLogHandler {

    private final AuditLogService auditLogService;

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void handle(AuditLogEvent event) {
        AuditLogCmd cmd = AuditLogCmd.builder()
                .traceId(event.traceId())
                .empId(event.operatorEmpId())
                .bizAction(event.action())
                // 优先用 AOP 拿到的 BizAuth.bizType；fallback resourceType（保旧测试通过）
                .bizType(event.bizType() != null ? event.bizType() : event.resourceType())
                .resourceUrl(event.requestUrl() != null ? event.requestUrl() : event.resourceId())
                .requestMethod(event.requestMethod())
                .requestParams(event.requestParams() != null ? event.requestParams() : event.before())
                .responseStatus(event.responseStatus())
                .errorMsg(event.errorMsg())
                .ipAddress(event.ip())
                .userAgent(event.userAgent())
                .executionTime(event.executionTime() != null ? event.executionTime().intValue() : null)
                .reason(event.reason())
                .targetType(event.targetType())
                .targetId(event.targetId())
                .beforeSnapshot(event.before())
                .afterSnapshot(event.after())
                .addedItems(event.addedItems())
                .removedItems(event.removedItems())
                .build();

        auditLogService.log(cmd);
    }
}
