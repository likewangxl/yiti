package com.bank.branch.platform.performance.enums;

/**
 * 指标 / KPI 方案 / 目标方案 等通用生命周期状态.
 * <p>DRAFT (草稿) → PUBLISHED (已发布) → DISABLED (已停用).
 * <p>注: 由于生产 DDL 中状态字段默认值是 'ACTIVE', 实际映射规则由 Service 层决定.
 */
public enum MetricStatusEnum {
    /** 草稿. */
    DRAFT,
    /** 已发布 (可被引用). */
    PUBLISHED,
    /** 已停用 (不再允许引用, 但保留配置). */
    DISABLED,
    /** 生产 DDL 默认值, 等价于 PUBLISHED (兼容). */
    ACTIVE;

    public static boolean isValid(String v) {
        if (v == null) {
            return false;
        }
        for (MetricStatusEnum e : values()) {
            if (e.name().equals(v)) {
                return true;
            }
        }
        return false;
    }
}
