package com.bank.branch.platform.performance.controller.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 目标修正申请响应 DTO (V1.2 Q3.2c).
 *
 * <p>由 Controller/Facade 层将 Entity 装配成 DTO 后返回，避免直接暴露 Entity.
 *
 * <p>字段对齐生产 DDL §17 + V1.2 Q3 落地.
 */
@Data
public class TargetAdjustRespDTO {

    /** 申请 ID. */
    private String id;

    /** 目标方案 ID. */
    private String planId;

    /** 对象类型 EMP/ORG. */
    private String subjectType;

    /** 对象 ID. */
    private String subjectId;

    /** 周期键. */
    private String cycleKey;

    /** 状态 DRAFT/IN_APPROVAL/APPROVED/REJECTED. */
    private String status;

    /** 流程业务键. */
    private String businessKey;

    /** 流程实例 ID. */
    private String processInstanceId;

    /** 归属机构. */
    private String ownerOrgId;

    /** 备注（承载 adjustments + reason 的 JSON）. */
    private String remark;

    /** 申请人 empId. */
    private String createdBy;

    /** 申请人姓名（按 createdBy 反查 PT_USER）；用户已删时为 null. */
    private String createdByName;

    /** 申请人主机构名称（按 createdBy 反查 EXT_USER_ORG + EXT_ORG_INFO）；查不到为 null. */
    private String createdByOrgName;

    /** 申请时间. */
    private LocalDateTime createdTime;

    /** 最近更新人. */
    private String updatedBy;

    /** 最近更新时间. */
    private LocalDateTime updatedTime;
}
