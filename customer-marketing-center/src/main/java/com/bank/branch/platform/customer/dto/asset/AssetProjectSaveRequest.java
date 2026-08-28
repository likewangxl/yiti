package com.bank.branch.platform.customer.dto.asset;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 新建或修改资产立项草稿请求。 */
@Data
public class AssetProjectSaveRequest {
    @NotNull(message = "客户不能为空")
    private Long custId;
    private Long sourceTouchTaskId;
    private Long sourceWorklogId;
    @Size(max = 200, message = "项目名称不能超过200字")
    private String projectName;
    private String projectType;
    private String bizType;
    private String guaranteeType;
    private BigDecimal projectTotalInvestment;
    private BigDecimal projectLoanAmount;
    private BigDecimal creditAmount;
    private BigDecimal creditExposureAmount;
    private Boolean urgent;
    private Boolean keyProject;
    private Integer lockVersion;
    private List<String> attachmentIds;
}
