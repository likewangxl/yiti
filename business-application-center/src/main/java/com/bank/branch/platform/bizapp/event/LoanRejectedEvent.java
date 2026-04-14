package com.bank.branch.platform.bizapp.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 贷款申请审批驳回事件。
 * 在工作流完成且结果为驳回时发布。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoanRejectedEvent {

    /** 贷款申请ID */
    private String loanId;

    /** 申请编号 */
    private String applyNo;

    /** 客户ID */
    private String custId;

    /** 归属机构代码 */
    private String ownerOrgId;
}
