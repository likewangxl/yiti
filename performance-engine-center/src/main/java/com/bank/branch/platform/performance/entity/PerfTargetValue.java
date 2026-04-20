package com.bank.branch.platform.performance.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 目标值/基础值表 perf_target_value 贫血实体.
 *
 * <p>对齐 DDL：12 列，主键 varchar(32)（planId 统一 String，见模块 CLAUDE.md 关键设计原则 3）.
 * <p>唯一键：uk_plan_subject_cycle_metric(plan_id, subject_type, subject_id, cycle_key, metric_code)
 * <p>索引：idx_metric_code(metric_code), idx_subject(subject_type, subject_id, cycle_key)
 */
@Data
public class PerfTargetValue {

    /** 目标值ID（varchar(32) 主键）. */
    private String id;

    /** 目标方案ID（varchar(32)）. */
    private String planId;

    /** 对象类型：EMP/ORG. */
    private String subjectType;

    /** 对象ID（emp_id/org_code）. */
    private String subjectId;

    /** 周期键：2026 或 2026Q1 等. */
    private String cycleKey;

    /** 指标编码. */
    private String metricCode;

    /** 目标值（decimal(20,4)）. */
    private BigDecimal targetValue;

    /** 基础值（decimal(20,4)，可空）. */
    private BigDecimal baseValue;

    /** 创建人. */
    private String createdBy;

    /** 创建时间. */
    private LocalDateTime createdTime;

    /** 更新人. */
    private String updatedBy;

    /** 更新时间. */
    private LocalDateTime updatedTime;
}
