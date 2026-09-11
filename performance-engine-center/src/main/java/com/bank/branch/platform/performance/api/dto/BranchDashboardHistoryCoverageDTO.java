package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 历史金融行的按日机构/指标覆盖，不对缺日补零。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchDashboardHistoryCoverageDTO {

    private LocalDate dataDate;
    private int expected;
    private int received;
    private int expectedSubjects;
    private int receivedSubjects;
    private boolean complete;
    @Builder.Default
    private List<String> missingSubjects = new ArrayList<>();
    @Builder.Default
    private List<String> missing = new ArrayList<>();
}
