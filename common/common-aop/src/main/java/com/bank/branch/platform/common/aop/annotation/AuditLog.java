package com.bank.branch.platform.common.aop.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    String action();
    String resourceType();
    boolean reasonRequired() default false;

    /**
     * 是否由业务服务在自己的事务内写入结构化审计。
     *
     * <p>默认 {@code false}，保持现有控制器切面采集方式不变。高危配置变更需要
     * before/after 快照与集合差异时，由服务层同步调用持久化审计处理器；切面据此跳过
     * 通用空快照记录，避免重复审计，也保证审计失败可以使业务事务回滚。</p>
     */
    boolean serviceManaged() default false;
}
