package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 大屏数据源保存/更新请求.
 */
@Data
public class ScreenDatasourceSaveReqDTO {

    /** 名称 */
    @NotBlank
    private String dsName;

    /** TIMESERIES/SINGLE；WIDE_TABLE/KPI_RESULT 会被服务端强制 TIMESERIES */
    private String dsType;

    /** WIDE_TABLE / KPI_RESULT / KPI_DETAIL / M98_STAT / FREE_REPORT / CUSTOM_SQL */
    @NotBlank
    private String sourceKind;

    /** 数据归属条线：CORP / RETAIL / COMMON。新建客户端应显式提交。 */
    private String bizLine;

    /** 类型化配置 JSON（三形态见计划头部契约） */
    @NotBlank
    private String configJson;

    /** 允许的预设周期 JSON 数组 */
    private String timeParamJson;

    /** ACTIVE / DISABLED（空=ACTIVE） */
    private String status;

    /** 备注 */
    private String remark;

    /** 新建/更新数据源均会改变可执行配置，必须填写审计原因。 */
    @NotBlank
    private String reason;
}
