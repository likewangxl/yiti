package com.bank.branch.platform.customer.dto.marketing.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 恢复 AUTO 主办同步请求。 */
@Data
public class MarketingCustomerOwnershipRestoreRequest {

    @NotBlank
    @Size(max = 500)
    private String reason;

    @NotNull
    private Integer lockVersion;
}
