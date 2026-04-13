package com.bank.branch.platform.portal.event;

import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 产品负责人变更事件
 *
 * <p>发布时机：</p>
 * <ol>
 *   <li>通讯录员工修改"负责产品"字段后</li>
 *   <li>员工离职被清理负责产品时</li>
 *   <li>产品被逻辑删除时</li>
 * </ol>
 *
 * @see com.bank.branch.platform.portal.api.dto.ProductDTO
 */
@Value
public class ProductResponsibleUpdatedEvent {

    /** 产品ID */
    String productId;

    /** 产品代码 */
    String productCode;

    /** 变更前负责人工号列表 */
    List<String> beforeEmpIds;

    /** 变更后负责人工号列表 */
    List<String> afterEmpIds;

    /** 事件来源：PRODUCT_SIDE / ADDRBOOK_SIDE — 防止反向触发 */
    String source;

    /** 操作人工号 */
    String operatorEmpId;

    /** 事件发生时间 */
    LocalDateTime occurredAt;
}
