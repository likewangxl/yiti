package com.bank.branch.platform.customer.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 发起客户转交请求；接收人首位作为新主办，其余作为协办。 */
@Data
public class CustomerTransferCreateReqDTO {
    @NotBlank(message = "客户ID不能为空")
    private String custId;
    @NotEmpty(message = "至少选择一名接收客户经理")
    @Size(max = 20, message = "接收客户经理最多20人")
    private List<String> targetEmpIds;
    @NotBlank(message = "转交原因不能为空")
    @Size(max = 500, message = "转交原因不能超过500字")
    private String reason;
}
