package com.bank.branch.platform.customer.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 认领客户请求 DTO
 */
@Data
@Schema(description = "认领客户请求")
public class ClaimReqDTO {

    /** 客户ID（必填） */
    @NotBlank(message = "客户ID不能为空")
    @Schema(description = "客户ID", required = true)
    private String custId;
}
