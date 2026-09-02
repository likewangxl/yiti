package com.bank.branch.platform.customer.dto.marketing.tag;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 标签审批请求。 */
@Data
public class TagReviewRequest {
    @Size(max = 500)
    private String opinion;
    private Integer lockVersion;
}
