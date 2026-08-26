package com.bank.branch.platform.customer.dto.marketing.customer;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 营销客户资料受控修改请求。
 * id、统一社会信用代码、客户号、开户状态和主办权不属于本请求可修改范围。
 */
@Data
public class MarketingCustomerProfileUpdateRequest {

    @Size(max = 200)
    private String custName;
    @Size(max = 100)
    private String legalRepresentative;
    @Size(max = 200)
    private String contactPerson;
    @Size(max = 50)
    private String contactMobile;
    @Size(max = 500)
    private String registeredAddress;
    @Size(max = 500)
    private String businessAddress;
    @Size(max = 2000)
    private String businessScope;
    @Size(max = 50)
    private String industry;
    @Size(max = 50)
    private String groupType;
    @Size(max = 200)
    private String groupName;
    @Size(max = 50)
    private String customerType;
    @Size(max = 50)
    private String enterpriseType;
    private Integer isKeystone;
    @Size(max = 2000)
    private String customerDesc;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal registeredCapital;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal creditAmount;
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal creditExposureAmount;
    private Integer touchRestricted;

    @NotNull
    private Integer profileVersion;
    @NotNull
    private Integer lockVersion;
    @NotBlank
    @Size(max = 500)
    private String reason;

    /** 至少填写一个允许修改的业务字段。 */
    public boolean hasProfileChanges() {
        return custName != null || legalRepresentative != null || contactPerson != null
                || contactMobile != null || registeredAddress != null || businessAddress != null
                || businessScope != null || industry != null || groupType != null || groupName != null
                || customerType != null || enterpriseType != null || isKeystone != null
                || customerDesc != null || registeredCapital != null || creditAmount != null
                || creditExposureAmount != null || touchRestricted != null;
    }
}
