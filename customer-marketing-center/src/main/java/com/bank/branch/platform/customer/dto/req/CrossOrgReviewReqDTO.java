package com.bank.branch.platform.customer.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 跨机构营销审批请求。 */
@Data
public class CrossOrgReviewReqDTO {
    @NotBlank(message = "审批意见不能为空")
    @Size(max = 500, message = "审批意见不能超过500字")
    private String reason;
}
