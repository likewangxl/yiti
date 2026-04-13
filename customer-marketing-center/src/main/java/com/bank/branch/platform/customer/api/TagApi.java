package com.bank.branch.platform.customer.api;

import com.bank.branch.platform.customer.entity.CustTag;

import java.util.List;

/**
 * 标签对外查询接口。
 * <p>
 * 供其他模块（如 business-application-center）查询可用标签列表及标签详情使用。
 * 只提供只读查询，不暴露写操作。
 * </p>
 *
 * @author customer-marketing-center
 * @since V1.0
 */
public interface TagApi {

    /**
     * 查询所有启用状态的标签列表。
     * <p>
     * 用于其他模块的打标下拉选择，返回 status=ACTIVE 的标签，按 tagPriority 降序。
     * </p>
     *
     * @return 启用的标签列表
     */
    List<CustTag> listEnabled();

    /**
     * 按 ID 查询标签详情。
     *
     * @param id 标签ID
     * @return 标签实体，不存在时返回 null
     */
    CustTag getById(String id);
}
