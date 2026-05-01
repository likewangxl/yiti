package com.bank.branch.platform.bizapp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资产投放申请实体，对应 loan_apply 表。
 * <p>
 * 状态机：DRAFT -> IN_APPROVAL -> COMPLETED/REJECTED/CANCELLED。
 * 业务键格式：LOAN:{id}。
 * </p>
 */
@Data
@TableName("loan_apply")
public class LoanApply {

    /** 申请ID（UUID，32位去连字符），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 申请编号（LA+yyyyMMdd+6位序号），对应 apply_no */
    private String applyNo;

    /** 客户ID，逻辑外键->cust_master.id，对应 cust_id */
    private String custId;

    /** 来源触达任务ID，逻辑外键->touch_task.id，对应 source_touch_task_id */
    private String sourceTouchTaskId;

    /** 项目类型（字典PROJECT_TYPE），对应 project_type */
    private String projectType;

    /** 业务类型（字典BIZ_TYPE），对应 biz_type */
    private String bizType;

    /** 担保方式（字典GUARANTEE_TYPE），对应 guarantee_type */
    private String guaranteeType;

    /** 授信金额（元，保留4位小数），对应 credit_amount */
    private BigDecimal creditAmount;

    /** 敞口金额（元，保留4位小数），对应 credit_exposure_amount */
    private BigDecimal creditExposureAmount;

    /** 状态：DRAFT/IN_APPROVAL/COMPLETED/REJECTED/CANCELLED，对应 status */
    private String status;

    /** 流程业务键，固定格式LOAN:{id}，对应 business_key */
    private String businessKey;

    /** 流程实例ID，对应 process_instance_id */
    private String processInstanceId;

    /** 归属机构（ORG_CODE），对应 owner_org_id */
    private String ownerOrgId;

    /** 创建人工号，对应 created_by */
    private String createdBy;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 更新人工号，对应 updated_by */
    private String updatedBy;

    /** 更新时间，对应 updated_time */
    private LocalDateTime updatedTime;

    /** 逻辑删除：0=未删，1=已删，对应 deleted */
    private Integer deleted;
}
