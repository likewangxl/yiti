package com.bank.branch.platform.performance.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 目标方案表 perf_target_plan 贫血实体.
 *
 * <p>对齐 DDL：12 列，主键 varchar(32)（id / kpiSchemeId 统一 String，见模块 CLAUDE.md 关键设计原则 3）.
 * <p>唯一键：uk_plan_code(plan_code)
 * <p>索引：idx_status(status)
 */
@Data
public class PerfTargetPlan {

    /** 目标方案ID（varchar(32) 主键）. */
    private String id;

    /** 方案编码（唯一）. */
    private String planCode;

    /** 方案名称. */
    private String planName;

    /** 关联 KPI 方案ID（varchar(32)）. */
    private String kpiSchemeId;

    /** 目标维度：EMP/ORG. */
    private String targetDim;

    /** 目标周期：YEAR/QUARTER. */
    private String targetCycle;

    /** 生效日期. */
    private LocalDate effectiveDate;

    /** 状态：ACTIVE/DISABLED. */
    private String status;

    /** 创建人. */
    private String createdBy;

    /** 创建时间. */
    private LocalDateTime createdTime;

    /** 更新人. */
    private String updatedBy;

    /** 更新时间. */
    private LocalDateTime updatedTime;
}
