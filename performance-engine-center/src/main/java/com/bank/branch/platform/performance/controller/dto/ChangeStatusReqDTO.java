package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 指标状态变更请求 DTO。
 */
@Data
@Schema(description = "指标状态变更请求")
public class ChangeStatusReqDTO {

    /** V1.6 放开：支持启用/停用/草稿三向切换。 */
    @Schema(description = "目标状态：ACTIVE / DRAFT / DISABLED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "status 不能为空")
    @Pattern(regexp = "^(ACTIVE|DRAFT|DISABLED)$", message = "status 必须是 ACTIVE / DRAFT / DISABLED")
    private String status;

    /** 审计原因。 */
    @Schema(description = "变更原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "reason 不能为空")
    @Size(max = 200, message = "reason 长度不能超过 200")
    private String reason;
}
