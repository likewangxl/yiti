package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * KPI 方案下拉项（大屏 KPI_DETAIL 数据源配置用，仅 ACTIVE 方案）.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScreenKpiSchemeRespDTO {

    /** 方案编码 */
    private String schemeCode;

    /** 方案名称 */
    private String schemeName;
}
