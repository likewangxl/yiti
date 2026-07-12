package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 数据源配置态试跑请求（未落库前预览 10 行）.
 */
@Data
public class ScreenTryRunReqDTO {

    /** WIDE_TABLE / KPI_RESULT / CUSTOM_SQL */
    @NotBlank
    private String sourceKind;

    /** TIMESERIES / SINGLE */
    private String dsType;

    /** 类型化配置 JSON；WIDE_TABLE 试跑须已含 metrics[].slot（前端先保存再试跑，或由列表带出） */
    @NotBlank
    private String configJson;

    /** 预设周期 */
    private String period;

    private String dateFrom;

    private String dateTo;

    /** orgCode / empId */
    private Map<String, String> contextParams;

    /** 审计原因（高危） */
    private String reason;
}
