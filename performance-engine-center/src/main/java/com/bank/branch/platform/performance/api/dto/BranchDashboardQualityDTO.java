package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 批次数据质量摘要。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchDashboardQualityDTO {
    private int expected;
    private int received;
    /** 机构覆盖口径，避免把指标值数量误读为机构数量。 */
    private int expectedSubjects;
    private int receivedSubjects;
    @Builder.Default
    private List<String> missingSubjects = new ArrayList<>();
    @Builder.Default
    private List<String> missing = new ArrayList<>();
    private boolean mixedPeriod;
    private boolean selectedComplete;
    @Builder.Default
    private List<String> newerIncomplete = new ArrayList<>();
}
