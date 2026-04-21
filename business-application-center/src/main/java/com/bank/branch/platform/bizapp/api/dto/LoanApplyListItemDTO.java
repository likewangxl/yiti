package com.bank.branch.platform.bizapp.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资产投放申请列表条目展示 DTO（轻量）。
 * <p>
 * 用于 REST 层 listPage 接口返回，字段是 {@link LoanApplyDTO} 的严格子集：
 * <ul>
 *   <li>不含敏感/内部字段：deleted、businessKey、processInstanceId、updatedBy、updatedTime</li>
 *   <li>含冗余展示字段：custName（由 LoanApplyDTOConverter 批量填充）</li>
 * </ul>
 * 与 {@link LoanApplyDTO} 保持独立（无继承），允许未来各自演化。
 * </p>
 */
@Data
public class LoanApplyListItemDTO {

    /** 申请ID（UUID，32位去连字符） */
    private String id;

    /** 申请编号（LA+yyyyMMdd+6位序号） */
    private String applyNo;

    /** 客户ID */
    private String custId;

    /** 客户名称（冗余展示字段，由转换器批量填充） */
    private String custName;

    /** 授信金额（元，保留4位小数） */
    private BigDecimal creditAmount;

    /** 状态：DRAFT/IN_APPROVAL/COMPLETED/REJECTED/CANCELLED */
    private String status;

    /** 归属机构（ORG_CODE） */
    private String ownerOrgId;

    /** 创建时间 */
    private LocalDateTime createdTime;
}
