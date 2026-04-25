package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 仪表盘图表-数据系列 DTO（03 §C.1 ChartSeriesDTO）.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartSeriesDTO {

    /** 系列名（如「存款余额」） */
    private String name;

    /** 单位（如「亿元」「%」） */
    private String unit;

    /** 数据点（按时间维度排序，与 {@link ChartDataDTO#getXAxis()} 一一对应） */
    private List<BigDecimal> data;
}
