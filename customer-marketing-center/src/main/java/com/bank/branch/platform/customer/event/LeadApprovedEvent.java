package com.bank.branch.platform.customer.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 线索审批通过事件
 * 由 WorkflowCallbackListener 发布，LeadApprovedListener 监听后创建客户主档
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LeadApprovedEvent {

    /** 线索ID */
    private String leadId;

    /** 线索编号 */
    private String leadNo;

    /** 操作类型: CREATE / UPDATE / DELETE */
    private String leadOp;

    /** 源客户ID（UPDATE/DELETE 时有值） */
    private String sourceCustId;

    /** 归属机构代码 */
    private String ownerOrgId;

    /** 操作人 */
    private String operatorEmpId;
}
