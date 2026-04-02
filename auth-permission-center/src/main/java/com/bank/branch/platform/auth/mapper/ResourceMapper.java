package com.bank.branch.platform.auth.mapper;

import com.bank.branch.platform.auth.entity.PtResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 资源 Mapper 接口，操作 PT_RESOURCE 表。
 * <p>
 * 资源表同时承载菜单树和 API 权限两类数据，查询时注意以 IS_MENU 区分。
 * selectByUrlAndMethod 用于请求鉴权时的 URL 匹配查询，属于高频操作，
 * 建议结合 Redis 缓存使用。
 * </p>
 */
@Mapper
public interface ResourceMapper {

    /**
     * 根据资源ID查询资源。
     *
     * @param resourceId 资源ID
     * @return 资源实体，不存在时返回 null
     */
    PtResource selectByResourceId(String resourceId);

    /**
     * 查询资源列表，支持按状态和系统编号过滤。
     *
     * @param status  资源状态，为 null 时不过滤
     * @param sysCode 系统编号，为 null 时不过滤
     * @return 资源列表，按 MENU_RANK_NO 升序排列
     */
    List<PtResource> selectAll(@Param("status") Integer status,
                               @Param("sysCode") String sysCode);

    /**
     * 新增资源记录。
     *
     * @param resource 资源实体
     * @return 受影响行数
     */
    int insert(PtResource resource);

    /**
     * 按主键（resourceId）更新资源信息，使用动态 SET 仅更新非 null 字段。
     *
     * @param resource 包含 resourceId 及待更新字段的资源实体
     * @return 受影响行数
     */
    int updateById(PtResource resource);

    /**
     * 根据 URL、请求方法、系统编号精确匹配资源，用于请求鉴权。
     * 利用表上的唯一索引 uk_pt_resource_url_method_sys 实现高效查询。
     *
     * @param resourceUrl    资源URL
     * @param resourceMethod 请求方法（GET/POST/PUT/DELETE/*）
     * @param sysCode        系统编号
     * @return 资源实体，不存在时返回 null
     */
    PtResource selectByUrlAndMethod(@Param("resourceUrl") String resourceUrl,
                                    @Param("resourceMethod") String resourceMethod,
                                    @Param("sysCode") String sysCode);

    /**
     * 查询指定角色拥有权限的资源列表，通过 PT_ROLE_RESOURCE 关联。
     *
     * @param roleId 角色ID
     * @return 资源列表
     */
    List<PtResource> selectByRoleId(String roleId);

    /**
     * 统计指定父节点下的子资源数量，用于删除父节点前的校验。
     *
     * @param parentResourceId 父资源ID
     * @return 子资源数量
     */
    long countChildren(String parentResourceId);
}
