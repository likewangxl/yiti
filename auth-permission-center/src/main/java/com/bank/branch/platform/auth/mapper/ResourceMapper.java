package com.bank.branch.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
 * <p>
 * MyBatis-Plus 接入：继承 {@link BaseMapper} 后，{@code insert(T)} /
 * {@code updateById(T)} 由 BaseMapper 提供。
 * 自定义业务查询继续保留在本接口和 XML。
 * </p>
 */
@Mapper
public interface ResourceMapper extends BaseMapper<PtResource> {

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

    /**
     * 查询所有菜单资源（IS_MENU=1），按 MENU_RANK_NO 升序排列。
     * <p>用于"分配菜单"对话框组装菜单树（无 status / sysCode 过滤，避免被禁用菜单藏起来）。</p>
     *
     * @return 菜单资源列表（含分组节点和叶子菜单）
     */
    List<PtResource> selectMenus();

    /**
     * 查询挂在指定菜单 ID 集合下的接口资源 ID 列表（ISMENU=0 且 PARENT_RESOURCE_ID 命中）。
     * <p>仅提供资源关系查询；角色菜单分配不会据此自动授予接口。</p>
     *
     * @param menuIds 菜单 ID 列表（空列表会触发 SQL 报错，调用方需先判空）
     * @return 接口资源 ID 列表
     */
    List<String> selectInterfaceIdsByMenuIds(@Param("menuIds") List<String> menuIds);

    /**
     * 查询所有"公共基础接口" ID 列表（ISMENU=0 且 PARENT_RESOURCE_ID 为 NULL/空）。
     * <p>仅提供资源分类查询；角色菜单分配不会自动授予这些接口。</p>
     *
     * @return 公共接口资源 ID 列表
     */
    List<String> selectPublicInterfaceIds();

    /**
     * 批量查询启用资源（STATUS=0）的 URL，按资源 ID 集合。
     * <p>用于 getUserPermissions 一次性取全部可访问 URL，替代逐条 selectByResourceId 的 N+1。</p>
     *
     * @param ids 资源 ID 集合（调用方需保证非空）
     * @return 去重的启用资源 URL 列表
     */
    List<String> selectEnabledUrlsByResourceIds(@Param("ids") java.util.Collection<String> ids);
}
