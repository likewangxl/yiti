package com.bank.branch.platform.customer.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 客户维护人转交请求 DTO。
 * <p>
 * 用于 POST /api/customers/{custId}/claims/{claimId}/transfer 接口。
 * 转交操作属于高危操作，reason 必填，用于审计留痕。
 * </p>
 */
@Data
public class TransferReqDTO {

    /**
     * 目标维护人员工工号，不可为空。
     */
    @NotBlank(message = "目标维护人不能为空")
    private String toEmpId;

    /**
     * 转交原因，不可为空（高危操作，审计留痕）。
     */
    @NotBlank(message = "转交原因不能为空")
    private String reason;
}
