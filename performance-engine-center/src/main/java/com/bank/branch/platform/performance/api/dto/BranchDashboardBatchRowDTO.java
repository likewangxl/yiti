package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** 批次中的机构行；metricValues 只包含业务指标和计算结果，不含客户 PII。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchDashboardBatchRowDTO {
    private String orgCode;
    private LocalDate dataDate;
    @Builder.Default
    private Map<String, BigDecimal> metricValues = new LinkedHashMap<>();
}
