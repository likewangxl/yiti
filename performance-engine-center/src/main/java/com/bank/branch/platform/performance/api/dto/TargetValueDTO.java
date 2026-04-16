package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 目标值 DTO.
 * <p>v1.2: id / planId 统一 String.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetValueDTO {
    /** 目标值 ID (varchar 32). */
    private String id;
    /** 目标方案 ID (varchar 32). */
    private String planId;
    /** 对象类型: EMP/ORG. */
    private String subjectType;
    /** 对象 ID (emp_id / org_code). */
    private String subjectId;
    /** 周期键: 2026 / 2026Q1 / 202604. */
    private String cycleKey;
    private String metricCode;
    private BigDecimal targetValue;
    private BigDecimal baseValue;
}
