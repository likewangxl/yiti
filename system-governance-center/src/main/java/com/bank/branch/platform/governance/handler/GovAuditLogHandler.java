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
 * <p>
 * 实现 common-aop 中的 AuditLogHandler 接口，将 @AuditLog 注解产生的审计事件
 * 路由到 system-governance-center 的 AuditLogService 进行持久化。
 * </p>
 * <p>
 * 使用 @Primary 确保在 NoopAuditLogHandler 同时存在时优先使用本实现。
 * handle() 方法内部 try-catch，保证审计日志写入失败不影响业务主流程。
 * </p>
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class GovAuditLogHandler implements AuditLogHandler {

    private final AuditLogService auditLogService;

    /**
     * 处理审计日志事件
     * <p>
     * 将 AuditLogEvent 转换为 AuditLogCmd 并调用 AuditLogService 写入数据库。
     * 审计写入失败仅记录错误日志，不向上抛出异常，避免影响业务事务。
     * </p>
     *
     * @param event 审计日志事件
     */
    @Override
    public void handle(AuditLogEvent event) {
        try {
            AuditLogCmd cmd = AuditLogCmd.builder()
                    .bizAction(event.action())
                    .bizType(event.resourceType())
                    .resourceUrl(event.resourceId())
                    .empId(event.operatorEmpId())
                    .ipAddress(event.ip())
                    .userAgent(event.userAgent())
                    .requestParams(event.before())
                    .reason(event.reason())
                    .build();

            auditLogService.log(cmd);
        } catch (Exception e) {
            // 审计日志写入失败不能影响业务主流程，仅记录错误
            log.error("[GovAuditLogHandler] 审计日志写入失败, action={}, resourceType={}, operator={}, error={}",
                    event.action(), event.resourceType(), event.operatorEmpId(), e.getMessage(), e);
        }
    }
}
