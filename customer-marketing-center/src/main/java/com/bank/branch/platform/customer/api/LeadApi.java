package com.bank.branch.platform.customer.api;

import com.bank.branch.platform.customer.entity.CustLead;

/**
 * 线索对外查询接口。
 * <p>
 * 供其他模块查询线索详情使用。
 * 只提供只读查询，不暴露写操作（写操作通过 LeadController 暴露）。
 * </p>
 *
 * @author customer-marketing-center
 * @since V1.0
 */
public interface LeadApi {

    /**
     * 按 ID 查询线索详情。
     *
     * @param id 线索ID
     * @return 线索实体，不存在时返回 null
     */
    CustLead getById(String id);

    /**
     * 按线索编号查询线索详情。
     * <p>
     * leadNo 是对外展示的业务编号，格式为 LEAD_{yyyyMMdd}_{4位序号}。
     * </p>
     *
     * @param leadNo 线索编号
     * @return 线索实体，不存在时返回 null
     */
    CustLead getByLeadNo(String leadNo);
}
