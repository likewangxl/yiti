package com.bank.branch.platform.customer.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/** 跨机构营销申请列表与详情响应。 */
@Data
public class CrossOrgApplyRespDTO {
    private String id;
    private String applyNo;
    private String custId;
    private String custNo;
    private String custName;
    private String applicantEmpId;
    private String applicantName;
    private String applicantOrgId;
    private String applicantOrgName;
    private String mainManagerId;
    private String mainManagerName;
    private String mainOrgId;
    private String mainOrgName;
    private Integer applicantNotMainCheck;
    private Integer mainOrgDifferentCheck;
    private Integer applicantNoPerformanceCheck;
    private Integer applicantOrgNoPerformanceCheck;
    private LocalDateTime checkSnapshotTime;
    private String applyReason;
    private String status;
    private String generatedTouchTaskId;
    private String reviewedBy;
    private LocalDateTime reviewedTime;
    private String rejectReason;
    private LocalDateTime createdTime;
    /** 仅审核角色且申请仍处于 IN_APPROVAL 时为 true。 */
    private boolean canReview;
}
