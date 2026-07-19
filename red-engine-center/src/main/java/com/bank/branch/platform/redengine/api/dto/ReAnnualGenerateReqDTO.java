package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 驾驶舱-生成年度考核归档结果请求 DTO（高危操作，审计强制留痕）。
 * <p>{@code ReCockpitController.generateAnnualResult} 原仅 {@code @PathVariable Integer year}，
 * 属高危操作（权限矩阵标注、{@code P_RE_CKPT_ANNUAL} 仅 {@code R_RE_ORGREV} 授权）但
 * {@code @AuditLog} 未设 {@code reasonRequired=true}，也无 {@code reason} 入参通道——本次修复对齐
 * 同控制器 {@code executeOverdue}（{@link ReOverdueExecuteReqDTO}）已有的高危留痕模式补齐。</p>
 */
@Data
@Schema(description = "生成年度归档请求(高危操作，审计留痕)")
public class ReAnnualGenerateReqDTO {

    /** 生成原因(高危操作必填，审计留痕) */
    @Schema(description = "生成原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "生成原因不能为空")
    private String reason;
}
