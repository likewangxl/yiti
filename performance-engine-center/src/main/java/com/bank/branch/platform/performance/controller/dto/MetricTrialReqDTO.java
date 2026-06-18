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

    /** 业绩分配日期（可选，SQL :allocDate；留空后端兜底=数据日期）. */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate allocDate;

    /** 样本条数（可选，默认 20，上限 100）. */
    @Min(value = 1, message = "sampleSize 必须 >=1")
    @Max(value = 100, message = "sampleSize 不能超过 100")
    private Integer sampleSize;

    /** 附加 SQL 参数（可选）. */
    private Map<String, Object> params;

    /** 直接试运行（不读已存指标）：计算逻辑类型 SQL/EXPR；提供 sqlText 或 exprText 时生效. */
    private String calcLogicType;

    /** 直接试运行的基础维度 EMP/ORG/CUST（EXPR 场景按维度选宽表加载引用指标值）. */
    private String baseDim;

    /** 直接试运行的 SQL 文本（SQL 场景，未保存指标时由前端传入）. */
    private String sqlText;

    /** 直接试运行的 Groovy 表达式文本（EXPR 场景，未保存指标时由前端传入）. */
    private String exprText;
}
