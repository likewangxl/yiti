package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/** 资产立项中途加急申请，对应 MARKETING_ASSET_PROJECT_URGENT_APPLY。 */
@Data
@TableName("MARKETING_ASSET_PROJECT_URGENT_APPLY")
public class AssetProjectUrgentApply {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String urgentApplyNo;
    private String legacyUrgentApplyId;
    private Long assetProjectApplyId;
    private Long custId;
    private String requestedAtNodeKey;
    private String requestedAtTaskId;
    private String applyReason;
    private String status;
    private String activeDedupKey;
    private String businessKey;
    private String processInstanceId;
    private String requestedBy;
    private String requestedOrgId;
    private LocalDateTime requestedTime;
    private String reviewedBy;
    private LocalDateTime reviewedTime;
    private String approvalComment;
    private String recordStatus;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    @Version
    private Integer lockVersion;
}
