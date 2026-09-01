package com.bank.branch.platform.bizapp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 中台支持申请实体，对应 SUPPORT_REQUEST。 */
@Data
@TableName("SUPPORT_REQUEST")
public class SupportRequest {

    /** 来源类型仅作为应用层契约字段，旧表没有 source_type 列。 */
    @TableField(exist = false)
    private String sourceType;

    /** 申请ID（UUID，32位去连字符） */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    /** 申请编号 */
    private String requestNo;
    /** 同批提交分组ID */
    private String submitGroupId;
    /** 客户ID */
    private String custId;
    /** 来源触达任务ID */
    private String sourceTouchTaskId;
    /** 产品ID */
    private String productId;
    /** 承接部门ORG_CODE */
    private String supportDeptId;
    /** 其他需求/补充说明 */
    private String otherDemand;
    /** 派单人工号 */
    private String dispatchEmpId;
    /** 派单时间 */
    private LocalDateTime dispatchTime;
    /** 承接办理人工号 */
    private String assignedEmpId;
    /** 业务状态 */
    private String status;
    /** 流程业务键 */
    private String businessKey;
    /** 流程实例ID */
    private String processInstanceId;
    /** 发起侧归属机构 */
    private String ownerOrgId;
    /** 创建人工号 */
    private String createdBy;
    /** 创建时间 */
    private LocalDateTime createdTime;
    /** 更新人工号 */
    private String updatedBy;
    /** 更新时间 */
    private LocalDateTime updatedTime;
    /** 逻辑删除 */
    private Integer deleted;
}
