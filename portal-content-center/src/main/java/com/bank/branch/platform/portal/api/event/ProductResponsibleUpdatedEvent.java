package com.bank.branch.platform.portal.api.event;

import lombok.Value;
import java.util.List;

/**
 * 产品负责人列表变更事件（V1 进程内发布，AFTER_COMMIT 触发消费者）
 */
@Value
public class ProductResponsibleUpdatedEvent {
    String productId;
    List<String> removed;  // 被移除的 empId 列表
    List<String> added;    // 新增的 empId 列表
    String source;         // PRODUCT_SIDE / ADDRBOOK_SIDE — 防止反向触发
    String operatorEmpId;  // 操作人
}
