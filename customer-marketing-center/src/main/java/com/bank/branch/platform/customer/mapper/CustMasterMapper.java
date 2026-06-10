package com.bank.branch.platform.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.api.dto.CustomerFilterDTO;
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
 * <p>
 * MyBatis-Plus 接入：继承 {@link BaseMapper} 后，{@code insert(T)} /
 * {@code updateById(T)} 由 BaseMapper 提供。
 * selectById 因签名含 @Param 保留原 XML 实现。
 * </p>
 */
@Mapper
public interface CustMasterMapper extends BaseMapper<CustMaster> {

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
     * 客户信息同步：把外部统计表 {@code XAN_M98_CUST_STAT_SHOW3} 中、指定统计日期下、
     * 客户编号在 {@code CUST_MASTER} 不存在的客户，按 CUST_ID/CUST_NAME 去重后插入客户主档，
     * 并记录统计日期(statis_dt)与创建时间(created_time)。
     * <p>
     * 客户编号已存在的由 NOT EXISTS 过滤；客户名称唯一键(uk_cust_name)冲突由 INSERT IGNORE 静默跳过。
     * </p>
     *
     * @param statisDt 统计日期（yyyy-MM-dd），匹配 STATIS_DT
     * @return 实际新增的客户主档记录数
     */
    int syncNewCustomersFromStat(@Param("statisDt") String statisDt);

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

    // insert(T) 和 updateById(T) 由 MyBatis-Plus BaseMapper 提供

    /**
     * 按 ID 列表批量查询客户主档（含逻辑删除过滤）。
     * <p>
     * 供 CustomerQueryApi.listCustomers 使用，调用方需确保 ids 非空。
     * </p>
     *
     * @param ids 客户ID列表，不能为空
     * @return 客户主档列表
     */
    List<CustMaster> selectByIds(@Param("ids") List<String> ids);

    /**
     * 按关键词模糊搜索客户主档（匹配 cust_name / cust_no / unified_credit_code）。
     * <p>
     * 结果按 updated_time DESC 排序，最多返回 limit 条记录。
     * </p>
     *
     * @param keyword 搜索关键词，不能为空
     * @param limit   最大返回条数
     * @return 匹配的客户主档列表
     */
    List<CustMaster> searchByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

    /**
     * 按过滤条件统计客户数量。
     * <p>
     * 供 CustomerQueryApi.countCustomers 使用，默认过滤逻辑删除记录。
     * </p>
     *
     * @param filter 过滤条件
     * @return 符合条件的客户总数
     */
    long countByFilter(@Param("filter") CustomerFilterDTO filter);
}
