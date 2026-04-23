package com.bank.branch.platform.performance.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 历史回算请求 DTO（V1.1 Task P7.2，端点 POST /api/perf/recalc）.
 *
 * <p>对齐 03 §F.2 权威字段：
 * <ul>
 *   <li>{@code cycleType} (必填) — MONTHLY / QUARTERLY / YEARLY</li>
 *   <li>{@code cycleDateFrom} (必填) — 区间起始日期（含）</li>
 *   <li>{@code cycleDateTo}   (必填) — 区间截止日期（含，{@code >= cycleDateFrom}）</li>
 *   <li>{@code reason} (必填) — 回算原因，@AuditLog reasonRequired=true</li>
 * </ul>
 *
 * <p>V1.1 扩展字段（03 §F.2 中 {@code scope} 字段 V1.1 暂不实现 EMP/ORG 细粒度）:
 * <ul>
 *   <li>{@code metricCodes}（可选）— 指标编码列表；null/空列表表示"所有 ACTIVE 指标"</li>
 *   <li>{@code version}（必填）— 数据版本（等同于 sys_control.current_version）</li>
 * </ul>
 *
 * <p>V1.2 规划：新增 {@code scope: RecalcScopeDTO}（scopeType = ALL/EMP_IDS/ORG_CODES）
 * 支持按员工/机构细粒度回算。
 *
 * <p>审计：{@code @AuditLog(reasonRequired=true)} 对应本 DTO 的 {@code reason} 字段.
 */
@Data
@Schema(description = "历史回算请求（POST /api/perf/recalc）")
public class RecalcReqDTO {

    /** 周期类型：MONTHLY / QUARTERLY / YEARLY. */
    @Schema(description = "周期类型：MONTHLY / QUARTERLY / YEARLY",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "MONTHLY")
    @NotBlank(message = "cycleType 不能为空")
    @Pattern(regexp = "^(MONTHLY|QUARTERLY|YEARLY)$",
            message = "cycleType 必须是 MONTHLY / QUARTERLY / YEARLY 之一")
    private String cycleType;

    /** 起始日期（含）. */
    @Schema(description = "回算区间起始日期（含）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-01-01")
    @NotNull(message = "cycleDateFrom 不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate cycleDateFrom;

    /** 截止日期（含，>= cycleDateFrom）. */
    @Schema(description = "回算区间截止日期（含，>= cycleDateFrom）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-03-31")
    @NotNull(message = "cycleDateTo 不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate cycleDateTo;

    /**
     * 指标编码列表（可选，V1.1 扩展）.
     * <p>null 或空列表 → 回算所有 status=ACTIVE 指标.
     */
    @Schema(description = "指标编码列表（可选）；null 或空表示所有 ACTIVE 指标",
            example = "[\"M_EMP_A\",\"M_EMP_B\"]")
    private List<String> metricCodes;

    /** 数据版本（必填，V1.1 扩展）. */
    @Schema(description = "数据版本（与 sys_control.current_version 对齐）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "v20260401")
    @NotBlank(message = "version 不能为空")
    @Size(max = 32, message = "version 长度不能超过 32")
    private String version;

    /** 回算原因（高危操作必填，@AuditLog reasonRequired=true）. */
    @Schema(description = "回算原因（高危操作必填）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "补录 Q1 数据")
    @NotBlank(message = "reason 不能为空")
    @Size(max = 1000, message = "reason 长度不能超过 1000")
    private String reason;
}
