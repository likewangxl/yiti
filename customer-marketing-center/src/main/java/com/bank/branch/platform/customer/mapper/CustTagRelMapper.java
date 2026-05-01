package com.bank.branch.platform.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.CustTagRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客户-标签关联 Mapper 接口，操作 cust_tag_rel 表。
 * <p>
 * 该表无逻辑删除字段，所有删除操作均为物理删除。
 * </p>
 * <p>
 * MyBatis-Plus 接入：继承 {@link BaseMapper} 后，{@code insert(T)} 由 BaseMapper 提供。
 * </p>
 */
@Mapper
public interface CustTagRelMapper extends BaseMapper<CustTagRel> {

    /**
     * 查询某客户的所有标签关联（用于客户详情展示标签）。
     *
     * @param custId 客户ID
     * @return 该客户的标签关联列表
     */
    List<CustTagRel> selectByCustId(@Param("custId") String custId);

    /**
     * 查询某标签关联的所有客户（用于标签详情页反查客户）。
     *
     * @param tagId 标签ID
     * @return 关联该标签的客户关联列表
     */
    List<CustTagRel> selectByTagId(@Param("tagId") String tagId);

    /**
     * 查询某客户与某标签的关联记录（用于追加打标幂等判断）。
     *
     * @param custId 客户ID
     * @param tagId  标签ID
     * @return 关联实体，不存在则返回 null
     */
    CustTagRel selectByCustIdAndTagId(@Param("custId") String custId, @Param("tagId") String tagId);

    /**
     * 物理删除指定客户与指定标签的关联记录（取消打标）。
     *
     * @param custId 客户ID
     * @param tagId  标签ID
     * @return 受影响行数
     */
    int deleteByCustIdAndTagId(@Param("custId") String custId, @Param("tagId") String tagId);

    /**
     * 物理删除某标签的所有客户关联（标签停用前清理用）。
     *
     * @param tagId 标签ID
     * @return 受影响行数
     */
    int deleteByTagId(@Param("tagId") String tagId);

    // insert(T) 由 MyBatis-Plus BaseMapper 提供

    /**
     * 批量插入关联记录（批量打标时使用）。
     *
     * @param list 关联实体列表
     * @return 受影响行数
     */
    int insertBatch(@Param("list") List<CustTagRel> list);

    /**
     * 查询某客户的所有标签 ID 列表（用于 getCustomerTags）。
     *
     * @param custId 客户 ID
     * @return 该客户的标签 ID 列表
     */
    List<String> selectTagIdsByCustId(@Param("custId") String custId);

    /**
     * 批量查询多个客户的标签关联（用于 batchGetCustomerTags，避免 N+1）。
     *
     * @param custIds 客户 ID 列表
     * @return 关联实体列表
     */
    List<CustTagRel> selectByCustIds(@Param("custIds") List<String> custIds);

    /**
     * 查询打了某标签的所有客户 ID 列表（用于 getCustomerIdsByTag）。
     *
     * @param tagId 标签 ID
     * @return 客户 ID 列表
     */
    List<String> selectCustIdsByTagId(@Param("tagId") String tagId);
}
