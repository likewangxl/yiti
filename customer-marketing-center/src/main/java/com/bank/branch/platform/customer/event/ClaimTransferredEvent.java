package com.bank.branch.platform.customer.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户维护人转交事件
 * 转交操作完成后发布
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClaimTransferredEvent {

    /** 认领记录ID */
    private String claimId;

    /** 客户ID */
    private String custId;

    /** 原维护人 */
    private String fromEmpId;

    /** 新维护人 */
    private String toEmpId;

    /** 操作人 */
    private String operatorEmpId;
}
