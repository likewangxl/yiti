package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 仪表盘图表数据 DTO（03 §C.1 ChartDataDTO）.
 *
 * <p>用于「全行存款趋势」「全行贷款趋势」等折线图.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartDataDTO {

    /** 图表标题（如「全行存款趋势」） */
    private String title;

    /** X 轴标签（通常是月份字符串：YYYY-MM） */
    private List<String> xAxis;

    /** 数据系列列表（V1.0 通常单系列） */
    private List<ChartSeriesDTO> series;
}
