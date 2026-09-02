package com.bank.branch.platform.bizapp.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 中台支持申请列表条目展示 DTO（轻量）。
 * <p>
 * 用于 REST 层 listPage 接口返回，字段按列表展示独立定义：
 * <ul>
 *   <li>含实体业务字段：custId、productId、supportDeptId、otherDemand、assignedEmpId、createdBy</li>
 *   <li>不含敏感/内部字段：deleted、businessKey、processInstanceId、updatedBy、updatedTime</li>
 *   <li>含冗余展示字段：custName、productName、supportDeptName（由转换器批量填充）</li>
 * </ul>
 * 与 {@link SupportRequestDTO} 保持独立（无继承），并额外包含当前节点和 SLA 展示字段。
 * </p>
 */
@Data
public class SupportRequestListItemDTO {

    /** 申请ID（UUID，32位去连字符） */
    private String id;

    /** 申请编号（SR+yyyyMMdd+6位序号） */
    private String requestNo;

    /** 同批提交分组ID（多产品拆单时共享） */
    private String submitGroupId;

    /** 客户ID */
    private String custId;

    /** 客户名称（冗余展示字段） */
    private String custName;

    /** 产品ID（拆单后单个产品） */
    private String productId;

    /** 产品名称（冗余展示字段） */
    private String productName;

    /** 承接部门ID（场景B） */
    private String supportDeptId;

    /** 承接部门名称（冗余展示字段） */
    private String supportDeptName;

    /** 其他需求/补充说明 */
    private String otherDemand;

    /** 支持场景（A=产品直达/B=部门承接） */
    private String scenario;

    /** 承接办理人员工号 */
    private String assignedEmpId;

    /** 发起人员工号 */
    private String createdBy;

    /** 状态：DRAFT/IN_APPROVAL/IN_PROGRESS/COMPLETED/REJECTED/CANCELLED */
    private String status;

    /** 当前流程节点名称（草稿或已结束时为空） */
    private String currentNodeName;

    /** 当前流程节点 KEY（草稿或已结束时为空） */
    private String currentNodeKey;

    /** 当前流程节点 SLA：GREEN/YELLOW/RED（无活动节点时为空） */
    private String slaStatus;

    /** 创建时间 */
    private LocalDateTime createdTime;
}
