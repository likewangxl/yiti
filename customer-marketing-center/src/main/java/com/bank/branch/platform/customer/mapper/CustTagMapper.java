package com.bank.branch.platform.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.CustTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客户标签 Mapper 接口，操作 cust_tag 表。
 * <p>
 * 所有查询默认过滤逻辑删除记录（deleted = 0）。
 * </p>
 * <p>
 * MyBatis-Plus 接入：继承 {@link BaseMapper} 后，{@code insert(T)} /
 * {@code updateById(T)} 由 BaseMapper 提供。
 * selectById 因签名含 @Param 保留原 XML 实现。
 * </p>
 */
@Mapper
public interface CustTagMapper extends BaseMapper<CustTag> {

    /**
     * 按 id 查询标签（含逻辑删除过滤）。
     *
     * @param id 标签ID
     * @return 标签实体，不存在或已删除时返回 null
     */
    CustTag selectById(@Param("id") String id);

    /**
     * 按标签名称查询（用于唯一性预检）。
     *
     * @param tagName 标签名称
     * @return 标签实体，不存在或已删除时返回 null
     */
    CustTag selectByTagName(@Param("tagName") String tagName);

    /**
     * 按标签编码查询（用于业务代码引用）。
     *
     * @param tagCode 标签编码
     * @return 标签实体，不存在或已删除时返回 null
     */
    CustTag selectByTagCode(@Param("tagCode") String tagCode);

    /**
     * 分页查询标签列表。
     * <p>
     * keyword 模糊搜索 tag_name 和 tag_code，status 精确匹配。
     * </p>
     *
     * @param keyword 关键词（搜索 tag_name 和 tag_code），可为 null
     * @param status  状态过滤（ACTIVE/DISABLED），可为 null
     * @param offset  偏移量
     * @param limit   每页条数
     * @return 标签列表
     */
    List<CustTag> selectPage(@Param("keyword") String keyword,
                             @Param("status") String status,
                             @Param("offset") int offset,
                             @Param("limit") int limit);

    /**
     * 统计分页查询的总记录数（与 selectPage 共享 WHERE 条件）。
     *
     * @param keyword 关键词，可为 null
     * @param status  状态过滤，可为 null
     * @return 总记录数
     */
    long countPage(@Param("keyword") String keyword,
                   @Param("status") String status);

    /**
     * 查询所有启用状态的标签（用于打标选择列表）。
     *
     * @return 启用的标签列表，按 tag_priority 降序排列
     */
    List<CustTag> selectEnabled();

    // insert(T) 和 updateById(T) 由 MyBatis-Plus BaseMapper 提供

    /**
     * 查询所有启用状态的标签，按 tag_priority 升序、tag_name 升序排列。
     * 用于对外 TagApi.listEnabledTags()，兼容契约排序要求。
     *
     * @return 启用的标签列表
     */
    List<CustTag> selectEnabledSorted();

    /**
     * 按 ID 列表批量查询标签，只返回 status=ACTIVE 的记录。
     * 用于 getCustomerTags / batchGetCustomerTags，天然过滤 DISABLED。
     *
     * @param ids 标签 ID 列表
     * @return 启用状态的标签列表
     */
    List<CustTag> selectEnabledByIds(@Param("ids") List<String> ids);

    /**
     * 按标签名称统计记录数（含逻辑删除过滤），用于名称唯一性校验。
     *
     * @param tagName 标签名称
     * @return 同名标签数量
     */
    Long countByTagName(@Param("tagName") String tagName);
}
