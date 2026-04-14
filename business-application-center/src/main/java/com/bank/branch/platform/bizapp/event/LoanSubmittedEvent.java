package com.bank.branch.platform.bizapp.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 贷款申请提交事件。
 * 在 submitForApproval 成功启动工作流后发布。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoanSubmittedEvent {

    /** 贷款申请ID */
    private String loanId;

    /** 申请编号 */
    private String applyNo;

    /** 客户ID */
    private String custId;

    /** 归属机构代码 */
    private String ownerOrgId;

    /** 操作人工号 */
    private String operatorEmpId;
}
