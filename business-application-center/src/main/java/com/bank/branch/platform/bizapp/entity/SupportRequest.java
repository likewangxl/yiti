package com.bank.branch.platform.bizapp.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 中场支持申请实体，对应 support_request 表。
 * <p>
 * 支持多产品拆单（同组共享 submit_group_id），双视图 SUPPORT/SUPPORT_DEPT。
 * 场景A（产品直达）：assigned_emp_id=产品负责人，dispatch_emp_id=NULL。
 * 场景B（部门承接）：先秘书派单，再 assigned_emp_id 办理。
 * 状态机：DRAFT -> IN_APPROVAL -> IN_PROGRESS(仅B) -> COMPLETED/REJECTED/CANCELLED。
 * 业务键格式：SUPPORT:{id}。
 * </p>
 */
@Data
public class SupportRequest {

    /** 申请ID（UUID，32位去连字符），对应 id */
    private String id;

    /** 申请编号（SR+yyyyMMdd+6位序号），对应 request_no */
    private String requestNo;

    /** 同批提交分组ID（多产品拆单时共享），对应 submit_group_id */
    private String submitGroupId;

    /** 客户ID，逻辑外键->cust_master.id，对应 cust_id */
    private String custId;

    /** 来源触达任务ID，对应 source_touch_task_id */
    private String sourceTouchTaskId;

    /** 产品ID，逻辑外键->product_info.id，对应 product_id */
    private String productId;

    /** 承接部门ORG_CODE，对应 support_dept_id */
    private String supportDeptId;

    /** 其他需求/补充说明，对应 other_demand */
    private String otherDemand;

    /** 派单人工号（部门秘书，仅场景B），对应 dispatch_emp_id */
    private String dispatchEmpId;

    /** 派单时间，对应 dispatch_time */
    private LocalDateTime dispatchTime;

    /** 承接办理人工号，对应 assigned_emp_id */
    private String assignedEmpId;

    /** 状态：DRAFT/IN_APPROVAL/IN_PROGRESS/COMPLETED/REJECTED/CANCELLED，对应 status */
    private String status;

    /** 流程业务键，固定格式SUPPORT:{id}，对应 business_key */
    private String businessKey;

    /** 流程实例ID，对应 process_instance_id */
    private String processInstanceId;

    /** 归属机构（发起侧ORG_CODE），对应 owner_org_id */
    private String ownerOrgId;

    /** 创建人工号（发起人），对应 created_by */
    private String createdBy;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 更新人工号，对应 updated_by */
    private String updatedBy;

    /** 更新时间，对应 updated_time */
    private LocalDateTime updatedTime;

    /** 逻辑删除，对应 deleted */
    private Integer deleted;
}
