package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 批次使用的指标定义摘要。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchDashboardMetricContractDTO {
    private String metricCode;
    private String metricName;
    private String unit;
    private Integer decimalPlaces;
    private String baseDim;
    private Integer valSlot;
    private String metricCategory;
    private String description;
    private String metricDesc;
    private String status;
    /** 达成率的分子/分母口径；非比率指标为空。 */
    private String numeratorMetricCode;
    private String denominatorMetricCode;
}
