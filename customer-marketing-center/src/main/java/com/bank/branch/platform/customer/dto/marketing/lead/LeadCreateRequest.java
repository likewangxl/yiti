package com.bank.branch.platform.customer.dto.marketing.lead;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 页面三手工录入线索草稿请求；leadSource 由服务端固定为 MANUAL。 */
@Data
public class LeadCreateRequest {

    @NotBlank(message = "企业名称不能为空")
    @Size(max = 200, message = "企业名称长度不能超过200")
    private String custName;

    @NotBlank(message = "统一社会信用代码不能为空")
    @Pattern(regexp = "[0-9A-Z]{18}", message = "统一社会信用代码必须为18位大写字母或数字")
    private String unifiedCreditCode;

    private String leadType;
    private String custNo;
    private String legalRepresentative;
    private BigDecimal registeredCapital;
    private String registeredAddress;
    private String businessAddress;
    private String businessScope;
    private String contactPerson;
    private String contactMobile;
    private String industry;
    private String groupType;
    private String groupName;
    private String customerType;
    private String enterpriseType;
    private Integer isKeystone;
    private Integer isAccountOpenedSnapshot;
    private Integer touchRestricted;
    private String customerDesc;
    private BigDecimal creditAmount;
    private BigDecimal creditExposureAmount;
    private String distributionMode;
    private String mainManagerId;
    private String mainOrgId;
    private List<String> managerEmpIds;
    private List<Long> tagIds;
    private String remark;
}
