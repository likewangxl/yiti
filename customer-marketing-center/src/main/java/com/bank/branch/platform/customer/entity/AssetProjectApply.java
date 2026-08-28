package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 资产立项主申请，对应 MARKETING_ASSET_PROJECT_APPLY。 */
@Data
@TableName("MARKETING_ASSET_PROJECT_APPLY")
public class AssetProjectApply {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String applyNo;
    private String legacyApplyId;
    private Long custId;
    private Long sourceTouchTaskId;
    private Long sourceWorklogId;
    private String projectName;
    private String projectType;
    private String bizType;
    private String guaranteeType;
    private BigDecimal projectTotalInvestment;
    private BigDecimal projectLoanAmount;
    private BigDecimal creditAmount;
    private BigDecimal creditExposureAmount;
    private Integer isUrgent;
    private Integer isKeyProject;
    private String urgentSource;
    private String applicantEmpId;
    private String applicantOrgId;
    private String mainManagerIdSnapshot;
    private String mainOrgIdSnapshot;
    private String status;
    private String businessKey;
    private String processInstanceId;
    private LocalDateTime submittedTime;
    private LocalDateTime completedTime;
    private String recordStatus;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    @Version
    private Integer lockVersion;
}
