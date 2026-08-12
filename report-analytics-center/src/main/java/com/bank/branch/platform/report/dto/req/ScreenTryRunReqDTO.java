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

    /** 类型化配置 JSON；WIDE_TABLE 只需 metrics[].metricCode，服务端试跑时自动翻译槽位 */
    @NotBlank
    private String configJson;

    /** 预设周期 */
    private String period;

    private String dateFrom;

    private String dateTo;

    /** orgCode / empId */
    private Map<String, String> contextParams;

    /**
     * NAMED_GROUP 数据源试跑时必须显式指定待验证的机构组；服务端只取该组有效成员，
     * 不信任 contextParams 中的 orgCode。
     */
    private String testOrgGroupCode;

    /** 审计原因（高危） */
    @NotBlank
    private String reason;
}
