package com.bank.branch.platform.bizapp.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 中台支持申请列表条目展示 DTO（轻量）。
 * <p>
 * 用于 REST 层 listPage 接口返回，字段是 {@link SupportRequestDTO} 的严格子集：
 * <ul>
 *   <li>不含敏感/内部字段：deleted、businessKey、processInstanceId、updatedBy、updatedTime</li>
 *   <li>含冗余展示字段：custName、productName、supportDeptName（由转换器批量填充）</li>
 * </ul>
 * 与 {@link SupportRequestDTO} 保持独立（无继承），允许未来各自演化。
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

    /** 客户名称（冗余展示字段） */
    private String custName;

    /** 产品名称（冗余展示字段） */
    private String productName;

    /** 承接部门名称（冗余展示字段） */
    private String supportDeptName;

    /** 支持场景（A=产品直达/B=部门承接） */
    private String scenario;

    /** 状态：DRAFT/IN_APPROVAL/IN_PROGRESS/COMPLETED/REJECTED/CANCELLED */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdTime;
}
