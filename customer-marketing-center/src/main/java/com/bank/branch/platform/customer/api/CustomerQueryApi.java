package com.bank.branch.platform.customer.api;

import com.bank.branch.platform.customer.entity.CustClaim;
import com.bank.branch.platform.customer.entity.CustMaster;

import java.util.List;

/**
 * 客户主档对外查询接口（最重要，被多个模块依赖）。
 * <p>
 * 供 business-application-center、performance-engine-center 等模块查询客户主档信息使用。
 * 提供客户存在性校验、认领关系查询等常用判断方法，避免跨模块直连 Mapper。
 * </p>
 *
 * @author customer-marketing-center
 * @since V1.0
 */
public interface CustomerQueryApi {

    /**
     * 按 ID 查询客户主档。
     *
     * @param id 客户ID
     * @return 客户主档实体，不存在时返回 null
     */
    CustMaster getCustomer(String id);

    /**
     * 按客户编号查询客户主档。
     * <p>
     * custNo 是对外展示的业务编号，格式为 C{yyyyMMdd}{序号}。
     * </p>
     *
     * @param custNo 客户编号
     * @return 客户主档实体，不存在时返回 null
     */
    CustMaster getCustomerByCustNo(String custNo);

    /**
     * 校验客户是否有效（存在且未删除）。
     * <p>
     * 供业务申请等模块在创建申请前校验客户是否存在。
     * </p>
     *
     * @param custId 客户ID
     * @return true 表示客户存在，false 表示不存在或已删除
     */
    boolean isValidCustomer(String custId);

    /**
     * 校验指定机构是否已认领该客户（且认领记录存在）。
     * <p>
     * 用于权限校验：确保只有认领了该客户的机构才能发起相关业务申请。
     * </p>
     *
     * @param custId 客户ID
     * @param orgId  机构代码
     * @return true 表示该机构存在认领记录，false 表示未认领
     */
    boolean isClaimedByOrg(String custId, String orgId);

    /**
     * 查询客户的所有认领记录（含历史取消记录）。
     * <p>
     * 用于展示客户被哪些机构认领过，包括已取消的历史记录。
     * </p>
     *
     * @param custId 客户ID
     * @return 认领记录列表（含取消记录），不存在时返回空列表
     */
    List<CustClaim> getCustomerClaims(String custId);
}
