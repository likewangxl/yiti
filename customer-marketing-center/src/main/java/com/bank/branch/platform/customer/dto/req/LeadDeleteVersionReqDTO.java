package com.bank.branch.platform.customer.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建删除版本线索请求 DTO。
 */
@Data
public class LeadDeleteVersionReqDTO {

    /** 源客户ID（cust_master.id，必填） */
    @NotBlank(message = "源客户ID不能为空")
    private String sourceCustId;

    /** 删除原因（可选） */
    private String remark;
}
