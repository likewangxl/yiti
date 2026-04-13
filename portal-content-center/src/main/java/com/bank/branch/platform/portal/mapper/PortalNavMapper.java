package com.bank.branch.platform.portal.mapper;

import com.bank.branch.platform.portal.entity.PortalNav;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 网址导航 Mapper 接口，操作 portal_nav 表。
 * <p>
 * 支持按分类查询、排序管理、启用/禁用状态过滤等操作。
 * </p>
 */
@Mapper
public interface PortalNavMapper {

    /**
     * 插入新导航。
     *
     * @param entity 导航实体
     * @return 受影响行数
     */
    int insert(PortalNav entity);

    /**
     * 按 id 查询。
     *
     * @param id 导航ID
     * @return 导航实体，不存在时返回 null
     */
    PortalNav selectById(@Param("id") String id);

    /**
     * 按部分字段更新，使用动态 SET 仅更新非 null 字段。
     *
     * @param entity 包含 id 及待更新字段的导航实体
     * @return 受影响行数
     */
    int updateById(PortalNav entity);

    /**
     * 逻辑停用（将 status 设为 DISABLED）。
     *
     * @param id        导航ID
     * @param updatedBy 操作人
     * @return 受影响行数
     */
    int softDeleteById(@Param("id") String id, @Param("updatedBy") String updatedBy);

    /**
     * 查询所有启用的导航，按 sort_order 升序排列。
     *
     * @return 启用状态的导航列表
     */
    List<PortalNav> listActive();

    /**
     * 按分类查询启用的导航，按 sort_order 升序排列。
     *
     * @param category 导航分类
     * @return 匹配分类且启用的导航列表
     */
    List<PortalNav> listByCategory(@Param("category") String category);

    /**
     * 批量更新排序号。
     *
     * @param items 包含 id 和 sortOrder 的导航列表
     * @return 受影响行数
     */
    int updateSortOrderBatch(@Param("items") List<PortalNav> items);
}
