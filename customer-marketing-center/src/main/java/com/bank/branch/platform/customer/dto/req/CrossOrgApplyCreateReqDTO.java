package com.bank.branch.platform.customer.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新建跨机构营销申请请求。 */
@Data
public class CrossOrgApplyCreateReqDTO {
    @NotBlank(message = "客户ID不能为空")
    private String custId;
    @NotBlank(message = "申请原因不能为空")
    @Size(max = 500, message = "申请原因不能超过500字")
    private String reason;
}
