package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 客户指标快照 DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustMetricSnapshotDTO {
    private String custId;
    private LocalDate dataDate;
    private String version;
    private Map<String, BigDecimal> metricValues;
}
