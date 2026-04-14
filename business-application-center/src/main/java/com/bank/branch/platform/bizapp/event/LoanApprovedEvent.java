package com.bank.branch.platform.bizapp.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 贷款申请审批通过事件。
 * 在工作流完成且结果为通过时发布。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoanApprovedEvent {

    /** 贷款申请ID */
    private String loanId;

    /** 申请编号 */
    private String applyNo;

    /** 客户ID */
    private String custId;

    /** 归属机构代码 */
    private String ownerOrgId;

    /** 授信金额 */
    private BigDecimal creditAmount;
}
