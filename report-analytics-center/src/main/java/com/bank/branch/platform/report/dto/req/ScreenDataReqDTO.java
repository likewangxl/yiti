package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 大屏统一取数请求.
 */
@Data
public class ScreenDataReqDTO {

    /** 数据源 ID（/api/screen/data 必填；try-run 场景不使用） */
    @NotNull
    private Long dsId;

    /** 预设周期：LATEST/LAST_10D/LAST_1M/LAST_6M_EOM/RANGE（空=LATEST） */
    private String period;

    /** RANGE 时必填 yyyy-MM-dd */
    private String dateFrom;

    /** RANGE 时必填 yyyy-MM-dd */
    private String dateTo;

    /** 上下文参数：orgCode / empId（大屏路由参数透传） */
    private Map<String, String> contextParams;
}
