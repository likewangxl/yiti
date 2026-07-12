package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 大屏数据源响应.
 */
@Data
public class ScreenDatasourceRespDTO {

    private Long id;

    private String dsCode;

    private String dsName;

    /** TIMESERIES / SINGLE（前端组件联动过滤的依据） */
    private String dsType;

    private String sourceKind;

    private String configJson;

    private String timeParamJson;

    private String status;

    private String remark;

    private LocalDateTime createdTime;
}
