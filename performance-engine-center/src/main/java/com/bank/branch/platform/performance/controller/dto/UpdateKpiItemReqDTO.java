package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 更新 KPI 方案项请求 DTO (部分更新).
 *
 * <p>所有字段可空, null 表示不修改; 提供时需满足基本范围校验。
 */
@Data
@Schema(description = "更新 KPI 方案项请求 (部分更新)")
public class UpdateKpiItemReqDTO {

    /** 权重 (0~100). */
    @Schema(description = "权重 0~100")
    @DecimalMin(value = "0.0000", message = "weight 不能小于 0")
    @DecimalMax(value = "100.0000", message = "weight 不能大于 100")
    private BigDecimal weight;

    /** 加倍系数. */
    @Schema(description = "加倍系数")
    @DecimalMin(value = "0.0000", message = "multiplier 不能小于 0")
    private BigDecimal multiplier;

    /** 最低分. */
    @Schema(description = "最低分")
    @DecimalMin(value = "0.0000", message = "minScore 不能小于 0")
    private BigDecimal minScore;

    /** 最高分. */
    @Schema(description = "最高分")
    @DecimalMin(value = "0.0000", message = "maxScore 不能小于 0")
    private BigDecimal maxScore;

    /** 计分公式 (可空, null 表示不修改). */
    @Schema(description = "计分公式, 变量 actual/target/base/weight, 支持 min/max")
    @Size(max = 500, message = "formula 长度不能超过 500")
    private String formula;
}
