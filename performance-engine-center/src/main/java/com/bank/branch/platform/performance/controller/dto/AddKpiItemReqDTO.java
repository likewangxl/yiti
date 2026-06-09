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

    /** 指标维度 (EMP/ORG/CUST, 可空); 随所选指标固化落库, 便于列表/回显展示, 与指标 base_dim 一致. */
    @Schema(description = "指标维度 EMP/ORG/CUST")
    @Size(max = 8, message = "baseDim 长度不能超过 8")
    private String baseDim;

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

    /** 最低分 (可空, 默认 0); 允许负值, 不限制最小值 (计分公式可产生负分场景). */
    @Schema(description = "最低分, 默认 0, 允许负值")
    private BigDecimal minScore;

    /** 最高分 (可空, 默认 999999). */
    @Schema(description = "最高分, 默认 999999")
    @DecimalMin(value = "0.0000", message = "maxScore 不能小于 0")
    private BigDecimal maxScore;

    /** 计分公式 (可空, 前端已取消计分公式列), 变量 actual/target/base/weight, 支持 min/max. */
    @Schema(description = "计分公式 (可空), 变量 actual/target/base/weight, 支持 min/max")
    @Size(max = 500, message = "formula 长度不能超过 500")
    private String formula;

    /** SQL 表达式 (可空), 支持 #{slot} 占位符, 用于自定义取数/计算. */
    @Schema(description = "SQL 表达式, 支持 #{slot} 占位符")
    @Size(max = 2000, message = "sqlExpr 长度不能超过 2000")
    private String sqlExpr;
}
