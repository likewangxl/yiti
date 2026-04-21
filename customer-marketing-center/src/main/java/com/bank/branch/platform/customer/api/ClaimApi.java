package com.bank.branch.platform.customer.api;

import com.bank.branch.platform.customer.api.dto.CustClaimDTO;

import java.util.List;
import java.util.Optional;

/**
 * 认领查询 API。
 * <p>
 * 供跨模块查询认领关系使用，只暴露只读操作，仅返回有效认领（CLAIMED 状态）。
 * </p>
 * 被调用方：business-application-center / performance-engine-center
 *
 * @author customer-marketing-center
 * @since V1.0
 */
public interface ClaimApi {

    /**
     * 获取客户在指定机构的认领关系（仅 CLAIMED 状态）。
     *
     * @param custId  客户ID
     * @param orgCode 机构代码
     * @return CLAIMED 状态的认领 DTO；不存在或已取消时返回 empty
     */
    Optional<CustClaimDTO> getClaim(String custId, String orgCode);

    /**
     * 员工已认领的客户列表（maintainer_emp_id 且 claim_status=CLAIMED）。
     *
     * @param empId 员工工号
     * @return 该员工维护的有效认领记录列表，不存在时返回空列表
     */
    List<CustClaimDTO> getEmpClaims(String empId);

    /**
     * 机构已认领的客户列表（claim_status=CLAIMED）。
     *
     * @param orgCode 机构代码
     * @return 该机构下有效认领记录列表，不存在时返回空列表
     */
    List<CustClaimDTO> getOrgClaims(String orgCode);

    /**
     * 校验认领关系是否有效（CLAIMED）。
     *
     * @param custId  客户ID
     * @param orgCode 机构代码
     * @return true 表示存在 CLAIMED 状态的认领关系
     */
    boolean isClaimActive(String custId, String orgCode);

    /**
     * 统计员工认领客户数量（仅 CLAIMED 状态）。
     *
     * @param empId 员工工号
     * @return 有效认领数量
     */
    long countEmpClaims(String empId);

    /**
     * 统计机构认领客户数量（仅 CLAIMED 状态）。
     *
     * @param orgCode 机构代码
     * @return 有效认领数量
     */
    long countOrgClaims(String orgCode);
}
