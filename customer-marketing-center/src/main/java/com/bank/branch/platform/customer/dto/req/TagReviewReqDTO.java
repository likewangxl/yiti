package com.bank.branch.platform.customer.dto.req;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 客户标签审核请求。 */
@Data
public class TagReviewReqDTO {
    /** 审核意见；退回时必填。 */
    @Size(max = 500, message = "审核意见不能超过500字")
    private String reason;
}
