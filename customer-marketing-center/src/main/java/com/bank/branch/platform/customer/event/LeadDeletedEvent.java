package com.bank.branch.platform.customer.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 线索删除事件
 * 线索删除审批通过后发布，清理关联数据
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LeadDeletedEvent {

    /** 线索ID */
    private String leadId;

    /** 线索编号 */
    private String leadNo;

    /** 源客户ID */
    private String sourceCustId;

    /** 操作人 */
    private String operatorEmpId;
}
