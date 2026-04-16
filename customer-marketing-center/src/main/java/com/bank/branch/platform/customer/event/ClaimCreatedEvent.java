package com.bank.branch.platform.customer.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户认领成功事件
 * 认领成功后发布，触达任务监听此事件自动创建首次触达任务
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClaimCreatedEvent {

    /** 认领记录ID */
    private String claimId;

    /** 客户ID */
    private String custId;

    /** 认领机构代码 */
    private String orgId;

    /** 认领人（员工工号） */
    private String claimedBy;

    /** 维护人（默认为认领人） */
    private String maintainerEmpId;
}
