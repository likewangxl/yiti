package com.bank.branch.platform.customer.api;

import com.bank.branch.platform.customer.entity.CustClaim;

import java.util.List;

/**
 * 客户认领记录对外查询接口。
 * <p>
 * 供其他模块查询客户认领关系使用。
 * 只提供只读查询，不暴露认领/取消认领操作。
 * </p>
 *
 * @author customer-marketing-center
 * @since V1.0
 */
public interface ClaimApi {

    /**
     * 查询指定客户的所有认领记录（含历史取消记录）。
     *
     * @param custId 客户ID
     * @return 该客户的认领记录列表，不存在时返回空列表
     */
    List<CustClaim> getClaimsByCustomer(String custId);

    /**
     * 按客户ID和机构代码查询认领记录。
     * <p>
     * 可用于查询特定机构是否认领了某客户，以及认领的当前状态。
     * </p>
     *
     * @param custId 客户ID
     * @param orgId  机构代码
     * @return 认领记录实体，不存在时返回 null
     */
    CustClaim getClaimByCustIdAndOrgId(String custId, String orgId);
}
