package com.bank.branch.platform.performance.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 目标值 upsert 请求 DTO (单值).
 *
 * <p>UK (planId, subjectType, subjectId, cycleKey, metricCode) 由 DB 保证唯一,
 * 冲突走 ON DUPLICATE KEY UPDATE 更新 target_value / base_value.
 *
 * <p>cycleKey 支持的粒度 (pattern 为粗略校验, 细粒度由业务层保证):
 * <ul>
 *   <li>年度: 2026</li>
 *   <li>季度: 2026Q1</li>
 *   <li>月度: 202604</li>
 * </ul>
 */
@Data
@Schema(description = "目标值 upsert 请求 (单值)")
public class UpsertTargetValueReqDTO {

    /** 目标方案ID (varchar 32). */
    @Schema(description = "目标方案ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "planId 不能为空")
    @Size(max = 32, message = "planId 长度不能超过 32")
    private String planId;

    /** 对象类型: EMP / ORG. */
    @Schema(description = "对象类型: EMP/ORG", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "subjectType 不能为空")
    @Pattern(regexp = "^(EMP|ORG)$", message = "subjectType 必须是 EMP 或 ORG")
    private String subjectType;

    /** 对象ID (emp_id 或 org_code). */
    @Schema(description = "对象ID (emp_id / org_code)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "subjectId 不能为空")
    @Size(max = 64, message = "subjectId 长度不能超过 64")
    private String subjectId;

    /**
     * 周期键 粗略校验: 4 位年份, 可选 Q1-Q9 或 MM.
     * 示例: 2026 / 2026Q1 / 202604.
     */
    @Schema(description = "周期键 (2026 / 2026Q1 / 202604)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "cycleKey 不能为空")
    @Pattern(regexp = "^\\d{4}(Q\\d|M?\\d{2})?$", message = "cycleKey 格式非法, 须形如 2026 / 2026Q1 / 202604")
    private String cycleKey;

    /** 指标编码 (大写 + 数字 + 下划线). */
    @Schema(description = "指标编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "metricCode 不能为空")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "metricCode 必须以大写字母开头, 只允许大写字母、数字和下划线")
    private String metricCode;

    /** 目标值 (必填, decimal(20,4)). */
    @Schema(description = "目标值", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "targetValue 不能为空")
    private BigDecimal targetValue;

    /** 基础值 (可空, decimal(20,4)). */
    @Schema(description = "基础值 (可空)")
    private BigDecimal baseValue;

    /** 阶段名称 (可空). */
    @Schema(description = "阶段名称 (可空)")
    @Size(max = 100, message = "stageName 长度不能超过 100")
    private String stageName;

    /** 起始日期 (可空, yyyy-MM-dd). */
    @Schema(description = "起始日期 yyyy-MM-dd (可空)")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** 截止日期 (可空, yyyy-MM-dd). */
    @Schema(description = "截止日期 yyyy-MM-dd (可空)")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
}
