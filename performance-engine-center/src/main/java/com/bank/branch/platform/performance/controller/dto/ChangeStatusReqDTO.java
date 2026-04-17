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

    /** 当前实现仅支持停用。 */
    @Schema(description = "目标状态，当前仅支持 DISABLED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "status 不能为空")
    @Pattern(regexp = "^DISABLED$", message = "status 当前仅支持 DISABLED")
    private String status;

    /** 审计原因。 */
    @Schema(description = "变更原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "reason 不能为空")
    @Size(max = 200, message = "reason 长度不能超过 200")
    private String reason;
}
