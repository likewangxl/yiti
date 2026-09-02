package com.bank.branch.platform.customer.dto.marketing.tag;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 单条标签客户审批请求。 */
@Data
public class TagCustomerApprovalRequest {
    @Size(max = 500)
    private String opinion;
    private Integer lockVersion;
    /** 标签未审批时，确认先通过标签再继续处理当前客户。 */
    private boolean approveTag;
}
