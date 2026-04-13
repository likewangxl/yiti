package com.bank.branch.platform.portal.mapper;

import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.service.dto.ProductListQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 产品信息 Mapper 接口，操作 product_info 表。
 * <p>
 * 所有查询默认过滤逻辑删除记录（deleted = 0）。
 * responsible_emp_ids 字段通过 JsonStringListTypeHandler 自动转换 List&lt;String&gt; ↔ JSON 字符串。
 * </p>
 */
@Mapper
public interface ProductInfoMapper {

    /**
     * 插入新产品。
     *
     * @param entity 产品实体
     * @return 受影响行数
     */
    int insert(ProductInfo entity);

    /**
     * 按 id 查询（含逻辑删除过滤）。
     *
     * @param id 产品ID
     * @return 产品实体，不存在或已删除时返回 null
     */
    ProductInfo selectById(@Param("id") String id);

    /**
     * 按 id 查询并加 FOR UPDATE 行锁（D.5 编辑用）。
     *
     * @param id 产品ID
     * @return 产品实体，不存在或已删除时返回 null
     */
    ProductInfo selectByIdForUpdate(@Param("id") String id);

    /**
     * 按 productCode 查询（用于 D.4 唯一性预检）。
     *
     * @param productCode 产品代码
     * @return 产品实体，不存在或已删除时返回 null
     */
    ProductInfo selectByProductCode(@Param("productCode") String productCode);

    /**
     * 按部分字段更新（D.5），使用动态 SET 仅更新非 null 字段。
     *
     * @param entity 包含 id 及待更新字段的产品实体
     * @return 受影响行数
     */
    int updateById(ProductInfo entity);

    /**
     * 逻辑删除（D.6），将 deleted 标记为 1。
     *
     * @param id        产品ID
     * @param updatedBy 操作人
     * @return 受影响行数
     */
    int softDeleteById(@Param("id") String id, @Param("updatedBy") String updatedBy);

    /**
     * D.3 查询所有支持中场支持的产品（active + 未删除）。
     *
     * @return 支持中场支持的产品列表，按产品名称升序排列
     */
    List<ProductInfo> listSupportAvailable();

    /**
     * 批量按 id 列表查询（D.2 详情聚合时用）。
     *
     * @param ids 产品ID列表
     * @return 匹配的产品列表
     */
    List<ProductInfo> listByIds(@Param("ids") List<String> ids);

    /**
     * 分页查询产品列表。
     * <p>
     * keyword 模糊搜索 product_code 和 product_name，category 和 status 精确匹配，
     * 始终过滤逻辑删除记录（deleted=0）。
     * </p>
     *
     * @param keyword  关键词（搜索 product_code 和 product_name），可为 null
     * @param category 产品类别，可为 null
     * @param status   状态过滤，可为 null
     * @param offset   偏移量
     * @param limit    每页条数
     * @return 产品列表
     */
    List<ProductInfo> selectPage(@Param("keyword") String keyword,
                                 @Param("category") String category,
                                 @Param("status") String status,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    /**
     * 统计分页查询的总记录数。
     *
     * @param keyword  关键词（搜索 product_code 和 product_name），可为 null
     * @param category 产品类别，可为 null
     * @param status   状态过滤，可为 null
     * @return 总记录数
     */
    long countPage(@Param("keyword") String keyword,
                   @Param("category") String category,
                   @Param("status") String status);

    /**
     * 按产品部门机构编码查询产品列表（ProductApi.listProductsByDept 用）。
     * 仅返回未删除的产品记录。
     *
     * @param productDeptOrgCode 产品部门机构编码
     * @return 该部门维护的全部产品列表
     */
    List<ProductInfo> listByProductDeptOrgCode(@Param("productDeptOrgCode") String productDeptOrgCode);

    /**
     * D.1 分页查询产品列表（含 DATA_SCOPE 过滤）。
     *
     * @param query 查询参数（含数据权限范围）
     * @return 当前页产品列表
     */
    List<ProductInfo> listProducts(@Param("q") ProductListQuery query);

    /**
     * D.1 统计产品总数（与 listProducts 共享 WHERE 条件）。
     *
     * @param query 查询参数（含数据权限范围）
     * @return 总记录数
     */
    long countProducts(@Param("q") ProductListQuery query);
}
