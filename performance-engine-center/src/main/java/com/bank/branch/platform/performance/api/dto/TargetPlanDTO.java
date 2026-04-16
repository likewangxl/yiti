package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 目标方案 DTO.
 * <p>v1.2: id / kpiSchemeId 统一 String (对齐生产 DDL varchar(32)).
 * <p>注: 04 契约原用 Long planId, 列为技术债, 后续由架构师统一处理.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetPlanDTO {
    /** 目标方案 ID (varchar 32). */
    private String id;
    private String planCode;
    private String planName;
    /** 关联 KPI 方案 ID (varchar 32). */
    private String kpiSchemeId;
    /** 目标维度: EMP/ORG. */
    private String targetDim;
    /** 目标周期: YEAR/QUARTER. */
    private String targetCycle;
    private LocalDate effectiveDate;
    /** 状态: ACTIVE/DISABLED. */
    private String status;
}
