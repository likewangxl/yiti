package com.bank.branch.platform.customer.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

/**
 * 认领客户请求 DTO
 */
@Data
@Schema(description = "认领客户请求")
public class ClaimReqDTO {

    /** 客户ID（必填） */
    @Schema(description = "兼容旧客户主档 ID；新接口优先传 sourceLeadId")
    private String custId;

    /** 目标营销表来源线索 ID。 */
    @Schema(description = "来源线索 ID")
    private Long sourceLeadId;

    @AssertTrue(message = "客户ID或来源线索ID至少填写一项")
    public boolean hasIdentifier() {
        return (custId != null && !custId.isBlank()) || sourceLeadId != null;
    }
}
