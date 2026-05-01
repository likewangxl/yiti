package com.bank.branch.platform.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.CustClaim;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客户认领关系 Mapper 接口，操作 cust_claim 表。
 * <p>
 * 该表无逻辑删除字段，有效认领通过 claim_status='CLAIMED' 过滤。
 * 客户池查询：cust_master 中尚未被本机构认领的客户。
 * </p>
 * <p>
 * MyBatis-Plus 接入：继承 {@link BaseMapper} 后，{@code insert(T)} /
 * {@code updateById(T)} 由 BaseMapper 提供。
 * selectById 因签名含 @Param 保留原 XML 实现。
 * </p>
 */
@Mapper
public interface CustClaimMapper extends BaseMapper<CustClaim> {

    /**
     * 按 id 查询认领记录。
     *
     * @param id 认领记录ID
     * @return 认领实体，不存在时返回 null
     */
    CustClaim selectById(@Param("id") String id);

    /**
     * 按客户ID和机构代码查询认领记录（防重复认领用）。
     *
     * @param custId 客户ID
     * @param orgId  机构代码
     * @return 认领实体，不存在时返回 null
     */
    CustClaim selectByCustIdAndOrgId(@Param("custId") String custId, @Param("orgId") String orgId);

    /**
     * 查询某客户的所有认领记录（含取消的历史记录）。
     *
     * @param custId 客户ID
     * @return 该客户的认领记录列表
     */
    List<CustClaim> selectByCustId(@Param("custId") String custId);

    /**
     * 查询某员工认领的所有有效客户记录（claim_status='CLAIMED'）。
     *
     * @param claimedBy 认领人（员工工号）
     * @return 该员工的有效认领记录列表
     */
    List<CustClaim> selectByClaimedBy(@Param("claimedBy") String claimedBy);

    /**
     * 分页查询客户池（尚未被任何机构有效认领的客户）。
     * <p>
     * LEFT JOIN cust_claim 过滤掉已认领客户，keyword 模糊搜索 cust_name。
     * </p>
     *
     * @param keyword 关键词（搜索 cust_name），可为 null
     * @param offset  偏移量
     * @param limit   每页条数
     * @return 未被认领的客户主档列表
     */
    List<com.bank.branch.platform.customer.entity.CustMaster> selectPoolPage(
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 统计客户池分页的总记录数（与 selectPoolPage 共享 WHERE 条件）。
     *
     * @param keyword 关键词，可为 null
     * @return 未被认领的客户总数
     */
    long countPoolPage(@Param("keyword") String keyword);

    /**
     * 分页查询某员工的认领客户列表（我的客户）。
     *
     * @param empId  员工工号
     * @param offset 偏移量
     * @param limit  每页条数
     * @return 该员工有效认领的客户认领记录列表
     */
    List<CustClaim> selectMyClaimsPage(@Param("empId") String empId,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    /**
     * 统计某员工的认领客户总数（与 selectMyClaimsPage 共享 WHERE 条件）。
     *
     * @param empId 员工工号
     * @return 该员工有效认领的客户总数
     */
    long countMyClaimsPage(@Param("empId") String empId);

    // insert(T) 由 MyBatis-Plus BaseMapper 提供

    /**
     * 查询某员工维护的所有有效认领记录（maintainer_emp_id 且 claim_status='CLAIMED'）。
     *
     * @param empId 员工工号
     * @return 该员工维护的有效认领记录列表
     */
    List<CustClaim> selectActiveByEmp(@Param("empId") String empId);

    /**
     * 查询某机构的所有有效认领记录（org_id 且 claim_status='CLAIMED'）。
     *
     * @param orgCode 机构代码
     * @return 该机构的有效认领记录列表
     */
    List<CustClaim> selectActiveByOrg(@Param("orgCode") String orgCode);

    /**
     * 统计某员工维护的有效认领数量（maintainer_emp_id 且 claim_status='CLAIMED'）。
     *
     * @param empId 员工工号
     * @return 有效认领数量
     */
    Long countActiveByEmp(@Param("empId") String empId);

    /**
     * 统计某机构的有效认领数量（org_id 且 claim_status='CLAIMED'）。
     *
     * @param orgCode 机构代码
     * @return 有效认领数量
     */
    Long countActiveByOrg(@Param("orgCode") String orgCode);

    // updateById(T) 由 MyBatis-Plus BaseMapper 提供
}
