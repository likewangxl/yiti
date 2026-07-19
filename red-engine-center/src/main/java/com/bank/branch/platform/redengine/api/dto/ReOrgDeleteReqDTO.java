package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 删除党组织请求 DTO（高危操作，审计强制留痕）。
 * <p>对齐 {@code customer-marketing-center} {@code TouchCancelReqDTO} 的既有模式
 * （见 {@code docs/code-examples.md}）：{@code @AuditLog(reasonRequired = true)} 本身不做任何
 * 强制校验，真正的强制手段是本 DTO 的 {@link #reason} 字段 {@code @NotBlank} + Controller 方法上的
 * {@code @Valid}；{@link com.bank.branch.platform.common.aop.AuditLogAspect#extractReason}
 * 再通过反射从 Controller 方法入参（含本 DTO）里找 {@code getReason()} 回填审计事件。
 * 原为遗留缺口——{@code ReOrgController.deleteOrg} 此前仅 {@code @PathVariable Long id}，
 * 路径变量无处挂载 {@code reason}，本次修复补齐（见 red-engine-center/CLAUDE.md「技术债」④，
 * 2026-07-19 已修复）。</p>
 */
@Data
@Schema(description = "删除党组织请求(高危操作，审计留痕)")
public class ReOrgDeleteReqDTO {

    /** 删除原因(高危操作必填，审计留痕) */
    @Schema(description = "删除原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "删除原因不能为空")
    private String reason;
}
