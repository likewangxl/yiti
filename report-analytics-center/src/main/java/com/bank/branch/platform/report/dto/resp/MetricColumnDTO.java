package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 动态查询结果列定义 DTO.
 *
 * <p>与 {@link DynamicQueryRespDTO#getRows()} 的 Map key 一一对应：
 * <ul>
 *   <li>{@code metricCode}：行中数据 key</li>
 *   <li>{@code metricName}：列头中文名</li>
 *   <li>{@code unit}：单位（V1.0 MetricDefDTO 尚未暴露该字段，留空）</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MetricColumnDTO {

    private String metricCode;

    private String metricName;

    private String unit;
}
