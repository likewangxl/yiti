package com.bank.branch.platform.customer.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户删除事件
 * 客户删除审批通过后发布，清理关联的标签、认领、触达数据
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomerDeletedEvent {

    /** 客户ID */
    private String custId;

    /** 客户编号 */
    private String custNo;

    /** 操作人 */
    private String operatorEmpId;
}
