package com.bank.branch.platform.common.aop.config;

import com.bank.branch.platform.common.aop.ApiLogAspect;
import com.bank.branch.platform.common.aop.AuditLogAspect;
import com.bank.branch.platform.common.aop.MethodTimingAspect;
import com.bank.branch.platform.common.aop.handler.AuditLogHandler;
import com.bank.branch.platform.common.aop.handler.NoopAuditLogHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class AopAutoConfiguration {

    @Bean
    public ApiLogAspect apiLogAspect() { return new ApiLogAspect(); }

    @Bean
    public MethodTimingAspect methodTimingAspect(
            @Value("${platform.method-timing-warn-ms:500}") long warnMs,
            @Value("${platform.method-timing-error-ms:5000}") long errorMs) {
        MethodTimingAspect aspect = new MethodTimingAspect();
        aspect.setWarnThresholdMs(warnMs);
        aspect.setErrorThresholdMs(errorMs);
        return aspect;
    }

    @Bean
    @ConditionalOnMissingBean(AuditLogHandler.class)
    public AuditLogHandler noopAuditLogHandler() { return new NoopAuditLogHandler(); }

    @Bean
    public AuditLogAspect auditLogAspect(AuditLogHandler handler) { return new AuditLogAspect(handler); }
}
