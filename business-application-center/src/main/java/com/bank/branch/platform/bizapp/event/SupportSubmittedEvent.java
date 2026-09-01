package com.bank.branch.platform.bizapp.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 中台支持申请提交事件。
 * 在申请从 DRAFT -> IN_APPROVAL 时发布。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SupportSubmittedEvent {

    /** 申请ID */
    private String requestId;

    /** 申请编号 */
    private String requestNo;

    /** 客户ID */
    private String custId;

    /** 产品ID（场景A有值，场景B为null） */
    private String productId;

    /** 归属机构 */
    private String ownerOrgId;

    /** 操作人工号 */
    private String operatorEmpId;
}
