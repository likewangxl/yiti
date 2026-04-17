package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 释放槽位请求 DTO。
 */
@Data
@Schema(description = "释放槽位请求")
public class ReleaseSlotReqDTO {

    /** 审计原因。 */
    @Schema(description = "释放原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "reason 不能为空")
    @Size(max = 200, message = "reason 长度不能超过 200")
    private String reason;
}
