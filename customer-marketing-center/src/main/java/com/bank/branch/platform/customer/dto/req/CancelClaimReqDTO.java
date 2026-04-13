package com.bank.branch.platform.customer.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 取消认领请求 DTO
 */
@Data
@Schema(description = "取消认领请求")
public class CancelClaimReqDTO {

    /** 取消原因（必填） */
    @NotBlank(message = "取消原因不能为空")
    @Schema(description = "取消原因", required = true)
    private String reason;
}
