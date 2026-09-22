package com.bank.branch.platform.report.dto.req.presentation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 各组件共享的类型化内容容器；组件类型决定使用哪些字段。 */
@Data
public class ScreenDisplayContentDTO {
    @Size(max = 100)
    private String mainField;
    private List<@Size(max = 100) String> subFields = new ArrayList<>();
    @Valid
    private List<ScreenDisplaySeriesDTO> series = new ArrayList<>();
    @Valid
    private List<ScreenDisplayColumnDTO> columns = new ArrayList<>();
    @Valid
    private List<ScreenDisplayTabDTO> tabs = new ArrayList<>();
    @Valid
    private List<ScreenDisplayRankingMetricDTO> rankingMetrics = new ArrayList<>();
}
