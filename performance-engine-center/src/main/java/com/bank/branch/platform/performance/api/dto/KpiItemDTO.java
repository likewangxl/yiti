package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * KPI 指标项 DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiItemDTO {
    /** 项 ID (varchar 32). */
    private String id;
    private String metricCode;
    private String metricName;
    private BigDecimal weight;
    private BigDecimal multiplier;
    private BigDecimal minScore;
    private BigDecimal maxScore;
    /** 计分公式（变量 actual/target/base/weight，支持 min/max）. */
    private String formula;
    private Integer sortNo;
}
