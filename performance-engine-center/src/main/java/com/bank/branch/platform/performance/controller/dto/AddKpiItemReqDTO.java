package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
    // 首字符必须是大写字母, 与 CreateKpiSchemeReqDTO.schemeCode 对齐, 避免 "123_FOO" 这类非法命名.
    // CreateMetricReqDTO.metricCode (Task 1 历史既定, 允许首字符为数字/下划线) 留作未来统一.
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "metricCode 首字符必须为大写字母, 且只允许大写字母、数字和下划线")
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
}
