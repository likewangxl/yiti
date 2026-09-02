package com.bank.branch.platform.customer.dto.marketing.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 主办权指定、转交或取消请求。 */
@Data
public class MarketingCustomerTransferRequest {

    /** ASSIGN、TRANSFER、UNASSIGN。 */
    @NotBlank
    private String transferAction;

    /** ASSIGN/TRANSFER 必填；UNASSIGN 必须为空。 */
    private String targetManagerId;

    @NotBlank
    @Size(max = 500)
    private String reason;

    @NotNull
    private Integer lockVersion;
}
