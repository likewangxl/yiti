package com.bank.branch.platform.customer.dto.req;

import lombok.Data;

/**
 * 客户删除申请请求 DTO。
 * <p>
 * 用于 POST /api/customers/{custId}/delete-apply 接口。
 * 删除操作需要走审批流，此 DTO 可携带申请理由供审批人参考。
 * </p>
 */
@Data
public class DeleteApplyReqDTO {

    /**
     * 删除原因（可为空，供审批人参考）。
     */
    private String reason;
}
