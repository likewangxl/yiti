package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增 KPI 方案项请求 DTO.
 *
 * <p>方案内指标项的基础配置; 由 Controller 层做 JSR-303 入参校验,
 * 业务规则 (同方案内 metricCode 唯一、metric 需存在等) 由 Service 层校验。
 */
@Data
@Schema(description = "新增 KPI 方案项请求")
public class AddKpiItemReqDTO {

    /** 指标编码 (引用 perf_metric_def.metric_code). */
    @Schema(description = "指标编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "metricCode 不能为空")
    @Size(max = 64, message = "metricCode 长度不能超过 64")
    // 引用既有指标，不做编码格式校验（格式校验只在"新建指标"CreateMetricReqDTO 处做）
    private String metricCode;

    /** 权重 (0.0000 ~ 100.0000). */
    @Schema(description = "权重 0~100", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "weight 不能为空")
    @DecimalMin(value = "0.0000", message = "weight 不能小于 0")
    @DecimalMax(value = "100.0000", message = "weight 不能大于 100")
    private BigDecimal weight;

    /** 加倍系数 (可空, 默认 1). */
    @Schema(description = "加倍系数, 默认 1")
    @DecimalMin(value = "0.0000", message = "multiplier 不能小于 0")
    private BigDecimal multiplier;

    /** 最低分 (可空, 默认 0). */
    @Schema(description = "最低分, 默认 0")
    @DecimalMin(value = "0.0000", message = "minScore 不能小于 0")
    private BigDecimal minScore;

    /** 最高分 (可空, 默认 999999). */
    @Schema(description = "最高分, 默认 999999")
    @DecimalMin(value = "0.0000", message = "maxScore 不能小于 0")
    private BigDecimal maxScore;

    /** 计分公式 (必输), 变量 actual/target/base/weight, 支持 min/max, 例 min(actual / target * 100, 120). */
    @Schema(description = "计分公式 (必输), 变量 actual/target/base/weight, 支持 min/max", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "计分公式不能为空")
    @Size(max = 500, message = "formula 长度不能超过 500")
    private String formula;
}
