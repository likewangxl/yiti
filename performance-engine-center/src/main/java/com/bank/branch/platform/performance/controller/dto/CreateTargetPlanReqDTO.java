package com.bank.branch.platform.performance.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 新建目标方案请求 DTO.
 *
 * <p>方案编码 (planCode) 为 UK, 不允许后续修改。DDL 实际允许值:
 * <ul>
 *   <li>targetDim: EMP / ORG (对齐 ddl-performance.sql L116 注释)</li>
 *   <li>targetCycle: YEAR / QUARTER (对齐 ddl-performance.sql L117 注释)</li>
 * </ul>
 *
 * <p>kpiSchemeId 引用的 KPI 方案必须为 ACTIVE 状态, Service 层会做二次校验
 * (PERF-40915 TARGET_PLAN_KPI_SCHEME_INVALID)。
 */
@Data
@Schema(description = "新建目标方案请求")
public class CreateTargetPlanReqDTO {

    /** 方案编码 (唯一, 大写 + 数字 + 下划线). */
    @Schema(description = "方案编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "planCode 不能为空")
    @Size(max = 64, message = "planCode 长度不能超过 64")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "planCode 必须以大写字母开头, 只允许大写字母、数字和下划线")
    private String planCode;

    /** 方案名称. */
    @Schema(description = "方案名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "planName 不能为空")
    @Size(max = 100, message = "planName 长度不能超过 100")
    private String planName;

    /** 关联 KPI 方案ID (varchar 32). */
    @Schema(description = "关联 KPI 方案ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "kpiSchemeId 不能为空")
    @Size(max = 32, message = "kpiSchemeId 长度不能超过 32")
    private String kpiSchemeId;

    /** 目标维度: EMP / ORG. */
    @Schema(description = "目标维度: EMP/ORG", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "targetDim 不能为空")
    @Pattern(regexp = "^(EMP|ORG)$", message = "targetDim 必须是 EMP 或 ORG")
    private String targetDim;

    /** 目标周期: YEAR / QUARTER. */
    @Schema(description = "目标周期: YEAR/QUARTER", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "targetCycle 不能为空")
    @Pattern(regexp = "^(YEAR|QUARTER)$", message = "targetCycle 必须是 YEAR 或 QUARTER")
    private String targetCycle;

    /** 生效日期 (必填). */
    @Schema(description = "生效日期 yyyy-MM-dd", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "effectiveDate 不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveDate;

    /** 方案覆盖起始日期（2026-05-22 新增；可空向后兼容，未来可改为必填）. */
    @Schema(description = "方案覆盖起始日期 yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** 方案覆盖截止日期（2026-05-22 新增；可空向后兼容）. */
    @Schema(description = "方案覆盖截止日期 yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
}
