package com.bank.branch.platform.portal.event;

import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 员工通讯录更新事件
 *
 * <p>发布时机：员工通讯录信息更新后，由 AddressBookService.updateEmployee()
 * 在事务提交后发布。</p>
 */
@Value
public class AddrbookUpdatedEvent {

    /** 员工工号 */
    String empId;

    /** 变更字段名列表（例如 ["mobile", "position"]） */
    List<String> changedFields;

    /** 操作人工号 */
    String operatorEmpId;

    /** 事件发生时间 */
    LocalDateTime occurredAt;
}
