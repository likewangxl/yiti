package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建指标请求 DTO。
 */
@Data
@Schema(description = "新建指标请求")
public class CreateMetricReqDTO {

    /** 指标编码。 */
    @Schema(description = "指标编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "metricCode 不能为空")
    @Size(max = 64, message = "metricCode 长度不能超过 64")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "metricCode 只允许大写字母、数字和下划线")
    private String metricCode;

    /** 指标名称。 */
    @Schema(description = "指标名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "metricName 不能为空")
    @Size(max = 200, message = "metricName 长度不能超过 200")
    private String metricName;

    /** 指标英文名称。 */
    @Schema(description = "指标英文名称")
    @Size(max = 200, message = "metricNameEn 长度不能超过 200")
    private String metricNameEn;

    /** 指标说明。 */
    @Schema(description = "指标说明")
    private String metricDesc;

    /** 基础维度。 */
    @Schema(description = "基础维度: EMP/ORG/CUST", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "baseDim 不能为空")
    @Pattern(regexp = "^(EMP|ORG|CUST)$", message = "baseDim 必须是 EMP、ORG 或 CUST")
    private String baseDim;

    /** 指标层级。 */
    @Schema(description = "指标层级: 1/2/3", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "metricLevel 不能为空")
    @Min(value = 1, message = "metricLevel 不能小于 1")
    @Max(value = 3, message = "metricLevel 不能大于 3")
    private Integer metricLevel;

    /** 计算频率。 */
    @Schema(description = "计算频率: DAY/MONTH/QUARTER/YEAR", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "calcFreq 不能为空")
    @Pattern(regexp = "^(DAY|MONTH|QUARTER|YEAR)$", message = "calcFreq 必须是 DAY、MONTH、QUARTER 或 YEAR")
    private String calcFreq;

    /** 计算模式。 */
    @Schema(description = "计算模式: AUTO/MANUAL", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "calcMode 不能为空")
    @Pattern(regexp = "^(AUTO|MANUAL)$", message = "calcMode 必须是 AUTO 或 MANUAL")
    private String calcMode;

    /** 计算逻辑类型。 */
    @Schema(description = "计算逻辑类型: SQL/PROC/EXPR/SUMMARY")
    @Pattern(regexp = "^(SQL|PROC|EXPR|SUMMARY)$", message = "calcLogicType 必须是 SQL、PROC、EXPR 或 SUMMARY")
    private String calcLogicType;

    /** SQL 文本。 */
    @Schema(description = "SQL 文本")
    private String sqlText;

    /** 表达式文本。 */
    @Schema(description = "表达式文本")
    private String exprText;

    /** 汇总规则。 */
    @Schema(description = "汇总规则: SUM/AVG/MAX/MIN/COUNT")
    @Pattern(regexp = "^(SUM|AVG|MAX|MIN|COUNT)$", message = "summaryRule 必须是 SUM、AVG、MAX、MIN 或 COUNT")
    private String summaryRule;

    /** 引用指标编码 JSON 字符串。 */
    @Schema(description = "引用指标编码 JSON 字符串")
    private String refMetricCodes;

    /** 指定槽位。 */
    @Schema(description = "指定槽位: 1~200")
    @Min(value = 1, message = "preferredSlot 不能小于 1")
    @Max(value = 200, message = "preferredSlot 不能大于 200")
    private Integer preferredSlot;
}
