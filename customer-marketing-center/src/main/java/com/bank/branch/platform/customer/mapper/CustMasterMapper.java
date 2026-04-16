package com.bank.branch.platform.customer.mapper;

import com.bank.branch.platform.customer.entity.CustMaster;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客户主档 Mapper 接口，操作 cust_master 表。
 * <p>
 * 所有查询默认过滤逻辑删除记录（deleted = 0）。
 * 客户可见性通过 cust_claim 认领关系判定，非 owner_org_id 字段。
 * </p>
 */
@Mapper
public interface CustMasterMapper {

    /**
     * 按 id 查询客户主档（含逻辑删除过滤）。
     *
     * @param id 客户ID
     * @return 客户主档实体，不存在或已删除时返回 null
     */
    CustMaster selectById(@Param("id") String id);

    /**
     * 按客户编号查询（用于对外展示和唯一性预检）。
     *
     * @param custNo 客户编号
     * @return 客户主档实体，不存在或已删除时返回 null
     */
    CustMaster selectByCustNo(@Param("custNo") String custNo);

    /**
     * 分页查询客户主档列表（含逻辑删除过滤）。
     * <p>
     * keyword 模糊搜索 cust_name 和 unified_credit_code，status 精确匹配。
     * </p>
     *
     * @param keyword 关键词（搜索 cust_name 和 unified_credit_code），可为 null
     * @param status  客户状态过滤（ACTIVE/INACTIVE），可为 null
     * @param offset  偏移量
     * @param limit   每页条数
     * @return 客户主档列表
     */
    List<CustMaster> selectPage(@Param("keyword") String keyword,
                                @Param("status") String status,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    /**
     * 统计分页查询的总记录数（与 selectPage 共享 WHERE 条件）。
     *
     * @param keyword 关键词，可为 null
     * @param status  客户状态过滤，可为 null
     * @return 总记录数
     */
    long countPage(@Param("keyword") String keyword,
                   @Param("status") String status);

    /**
     * 插入新客户主档。
     *
     * @param entity 客户主档实体
     * @return 受影响行数
     */
    int insert(CustMaster entity);

    /**
     * 按 id 更新客户主档（动态 SET，仅更新非 null 字段）。
     *
     * @param entity 包含 id 及待更新字段的客户主档实体
     * @return 受影响行数
     */
    int updateById(CustMaster entity);
}
