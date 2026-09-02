package com.bank.branch.platform.customer.entity.marketing;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 跨机构营销申请及四项校验快照，对应 {@code MARKETING_CROSS_ORG_APPLY}。
 * 状态严格使用 DRAFT/IN_APPROVAL/APPROVED/REJECTED。
 */
@Data
@TableName("MARKETING_CROSS_ORG_APPLY")
public class MarketingCrossOrgApply {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String applyNo;
    private Long custId;
    private String applicantEmpId;
    private String applicantOrgId;
    private String mainManagerIdSnapshot;
    private String mainOrgIdSnapshot;
    private Integer applicantNotMainCheck;
    private Integer mainOrgDifferentCheck;
    private Integer applicantNoPerformanceCheck;
    private Integer applicantOrgNoPerformanceCheck;
    private LocalDateTime checkSnapshotTime;
    private String applyReason;
    private String status;
    private Long generatedTouchTaskId;
    private String businessKey;
    private String processInstanceId;
    private String reviewedBy;
    private LocalDateTime reviewedTime;
    private String rejectReason;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;

    @Version
    private Integer lockVersion;
}
