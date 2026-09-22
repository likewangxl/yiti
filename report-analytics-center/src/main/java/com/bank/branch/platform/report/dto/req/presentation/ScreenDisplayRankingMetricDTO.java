package com.bank.branch.platform.report.dto.req.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 排名可切换指标。 */
@Data
public class ScreenDisplayRankingMetricDTO {
    @NotBlank
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{0,63}")
    private String metricKey;
    @NotBlank
    @Size(max = 100)
    private String field;
    @NotBlank
    @Size(max = 100)
    private String label;
    @NotNull
    private ScreenDisplayUnit unit;
    @NotNull
    private ScreenSortDirection direction;
}
