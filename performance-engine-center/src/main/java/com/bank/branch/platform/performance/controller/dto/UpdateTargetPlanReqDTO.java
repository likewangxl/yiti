package com.bank.branch.platform.performance.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 更新目标方案请求 DTO (部分更新).
 *
 * <p>所有字段可空, null 表示不修改。plan_code 是 UK 不允许修改;
 * kpiSchemeId 为安全起见也不对外开放 patch (若需切换应走"禁用旧方案+新建方案")。
 *
 * <p>Plan 文档 L1406 钦定: update 场景<strong>不</strong>强制 reason,
 * 空 body / 空 DTO 均应返 200。
 */
@Data
@Schema(description = "更新目标方案请求 (部分更新)")
public class UpdateTargetPlanReqDTO {

    /** 方案名称 (可空). */
    @Schema(description = "方案名称")
    @Size(max = 100, message = "planName 长度不能超过 100")
    private String planName;

    /** 目标维度 (可空, EMP/ORG). */
    @Schema(description = "目标维度: EMP/ORG")
    @Pattern(regexp = "^(EMP|ORG)$", message = "targetDim 必须是 EMP 或 ORG")
    private String targetDim;

    /** 目标周期 (可空, YEAR/QUARTER). */
    @Schema(description = "目标周期: YEAR/QUARTER")
    @Pattern(regexp = "^(YEAR|QUARTER)$", message = "targetCycle 必须是 YEAR 或 QUARTER")
    private String targetCycle;

    /** 生效日期 (可空). */
    @Schema(description = "生效日期 yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveDate;

    /** 方案覆盖起始日期 (可空，null 表示不修改). */
    @Schema(description = "方案覆盖起始日期 yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** 方案覆盖截止日期 (可空，null 表示不修改). */
    @Schema(description = "方案覆盖截止日期 yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
}
