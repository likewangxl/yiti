package com.bank.branch.platform.customer.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户认领对外 DTO
 * <p>
 * 用于跨模块传递客户认领记录信息，包含认领机构、维护人员及认领/取消时间等。
 * 一个客户同一时刻只能有一条有效认领记录，认领状态为 CLAIMED。
 * </p>
 */
@Data
public class CustClaimDTO {

    /** 认领记录 ID */
    private String id;

    /** 客户 ID */
    private String custId;

    /** 客户名称 (冗余, 避免二次查询) */
    private String custName;

    /** 认领机构 ID */
    private String orgId;

    /** 认领机构名称 */
    private String orgName;

    /** 维护人员工号 */
    private String maintainerEmpId;

    /** 维护人员姓名 */
    private String maintainerEmpName;

    /** 认领状态: CLAIMED / CANCELLED */
    private String claimStatus;

    /** 认领时间 */
    private LocalDateTime claimedAt;

    /** 取消时间 */
    private LocalDateTime cancelledAt;

    /** 取消原因 */
    private String cancelReason;
}
