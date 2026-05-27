package com.bank.branch.platform.governance.handler;

import com.bank.branch.platform.common.aop.event.AuditLogEvent;
import com.bank.branch.platform.common.aop.handler.AuditLogHandler;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.governance.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * <p>使用 @Primary 确保在 NoopAuditLogHandler 同时存在时优先使用本实现。
 * handle() 方法内部 try-catch，保证审计日志写入失败不影响业务主流程。</p>
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class GovAuditLogHandler implements AuditLogHandler {

    private final AuditLogService auditLogService;

    @Override
    public void handle(AuditLogEvent event) {
        try {
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
                    .build();

            auditLogService.log(cmd);
        } catch (Exception e) {
            log.error("[GovAuditLogHandler] 审计日志写入失败, action={}, resourceType={}, operator={}, error={}",
                    event.action(), event.resourceType(), event.operatorEmpId(), e.getMessage(), e);
        }
    }
}
