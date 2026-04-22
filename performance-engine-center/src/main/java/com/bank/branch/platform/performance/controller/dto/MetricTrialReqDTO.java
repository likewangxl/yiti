package com.bank.branch.platform.performance.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

/**
 * 指标试运行请求 DTO（03 §A.5）.
 *
 * <p>端点：{@code POST /api/perf/metrics/{metricCode}/trial-run}
 *
 * <p>字段：
 * <ul>
 *   <li>{@code dataDate}：试运行目标日期（必填）</li>
 *   <li>{@code sampleSize}：样本条数（可选，默认 20，最大 100）</li>
 *   <li>{@code params}：附加 SQL 参数（可选，SQL 场景生效）</li>
 * </ul>
 */
@Data
public class MetricTrialReqDTO {

    /** 数据日期（必填）. */
    @NotNull(message = "dataDate 不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataDate;

    /** 样本条数（可选，默认 20，上限 100）. */
    @Min(value = 1, message = "sampleSize 必须 >=1")
    @Max(value = 100, message = "sampleSize 不能超过 100")
    private Integer sampleSize;

    /** 附加 SQL 参数（可选）. */
    private Map<String, Object> params;
}
