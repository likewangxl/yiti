package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 跨机构客户营销申请。 */
@Data
@TableName("CROSS_ORG_MARKETING_APPLY")
public class CrossOrgMarketingApply {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String applyNo;
    private String custId;
    private String custNo;
    private String applicantEmpId;
    private String applicantOrgId;
    private String mainManagerId;
    private String mainOrgId;
    private Integer applicantNotMainCheck;
    private Integer mainOrgDifferentCheck;
    private Integer applicantNoPerformanceCheck;
    private Integer applicantOrgNoPerformanceCheck;
    private LocalDateTime checkSnapshotTime;
    private String applyReason;
    private String status;
    private String generatedTouchTaskId;
    private String businessKey;
    private String processInstanceId;
    private String reviewedBy;
    private LocalDateTime reviewedTime;
    private String rejectReason;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}
