package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 各数据源独立的截至日期。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchDashboardSourceAsOfDTO {
    private LocalDate financial;
    private LocalDate marketing;
    private LocalDate target;
    private LocalDate revenue;
    private LocalDate targetEffectiveDate;
    private LocalDateTime financialCollectedAt;
    private LocalDateTime marketingCollectedAt;
    private LocalDateTime targetCollectedAt;
    private LocalDateTime revenueCollectedAt;
}
