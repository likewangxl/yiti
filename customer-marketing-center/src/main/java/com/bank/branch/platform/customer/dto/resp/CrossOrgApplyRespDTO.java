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
    private String mainOrgId;
    private String applyReason;
    private String status;
    private String generatedTouchTaskId;
    private String reviewedBy;
    private LocalDateTime reviewedTime;
    private String rejectReason;
    private LocalDateTime createdTime;
}
