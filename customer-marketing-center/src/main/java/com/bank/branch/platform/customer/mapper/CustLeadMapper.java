package com.bank.branch.platform.customer.mapper;

import com.bank.branch.platform.customer.entity.CustLead;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客户线索 Mapper 接口，操作 cust_lead 表。
 * <p>
 * 所有查询默认过滤逻辑删除记录（deleted = 0）。
 * 版本管理：通过 is_latest 区分最新版本，版本链通过 prev_lead_id 追溯。
 * </p>
 */
@Mapper
public interface CustLeadMapper {

    /**
     * 按 id 查询线索（含逻辑删除过滤）。
     *
     * @param id 线索ID
     * @return 线索实体，不存在或已删除时返回 null
     */
    CustLead selectById(@Param("id") String id);

    /**
     * 按线索编号查询（用于对外展示和唯一性预检）。
     *
     * @param leadNo 线索编号
     * @return 线索实体，不存在或已删除时返回 null
     */
    CustLead selectByLeadNo(@Param("leadNo") String leadNo);

    /**
     * 查询某客户的最新版本线索（is_latest=1）。
     *
     * @param sourceCustId 源客户ID（cust_master.id）
     * @return 该客户的最新版本线索，不存在时返回 null
     */
    CustLead selectLatestBySourceCustId(@Param("sourceCustId") String sourceCustId);

    /**
     * 分页查询线索列表（只查最新版本 is_latest=1）。
     * <p>
     * keyword 模糊搜索 cust_name 和 unified_credit_code，status/ownerOrgId 精确匹配。
     * </p>
     *
     * @param keyword    关键词（搜索 cust_name 和 unified_credit_code），可为 null
     * @param status     线索状态过滤，可为 null
     * @param ownerOrgId 归属机构代码过滤，可为 null
     * @param offset     偏移量
     * @param limit      每页条数
     * @return 线索列表
     */
    List<CustLead> selectPage(@Param("keyword") String keyword,
                              @Param("status") String status,
                              @Param("ownerOrgId") String ownerOrgId,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

    /**
     * 统计分页查询的总记录数（与 selectPage 共享 WHERE 条件）。
     *
     * @param keyword    关键词，可为 null
     * @param status     线索状态过滤，可为 null
     * @param ownerOrgId 归属机构代码过滤，可为 null
     * @return 总记录数
     */
    long countPage(@Param("keyword") String keyword,
                   @Param("status") String status,
                   @Param("ownerOrgId") String ownerOrgId);

    /**
     * 插入新线索。
     *
     * @param entity 线索实体
     * @return 受影响行数
     */
    int insert(CustLead entity);

    /**
     * 按 id 更新线索（动态 SET，仅更新非 null 字段）。
     *
     * @param entity 包含 id 及待更新字段的线索实体
     * @return 受影响行数
     */
    int updateById(CustLead entity);

    /**
     * 按 id 更新线索状态（状态流转专用，减少并发冲突范围）。
     *
     * @param id         线索ID
     * @param leadStatus 目标状态
     * @param updatedBy  操作人
     * @return 受影响行数
     */
    int updateStatusById(@Param("id") String id,
                         @Param("leadStatus") String leadStatus,
                         @Param("updatedBy") String updatedBy);

    /**
     * 按 id 查询并加 FOR UPDATE 行锁（状态变更前用于并发保护）。
     *
     * @param id 线索ID
     * @return 线索实体，不存在或已删除时返回 null
     */
    CustLead selectForUpdate(@Param("id") String id);
}
