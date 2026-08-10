package com.bank.branch.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.auth.entity.PtRoleResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色资源关联 Mapper 接口，操作 PT_ROLE_RESOURCE 表。
 * <p>
 * 维护角色与接口资源/菜单的授权关系。批量操作（如重新授权）建议先 deleteByRoleId 再批量 insert，
 * 不使用 merge/upsert 以保证行为可预期。
 * </p>
 * <p>
 * MyBatis-Plus 接入：继承 {@link BaseMapper} 后，{@code insert(T)} 由 BaseMapper 提供。
 * 自定义业务查询和删除操作继续保留在本接口和 XML。
 * </p>
 */
@Mapper
public interface RoleResourceMapper extends BaseMapper<PtRoleResource> {

    /**
     * 查询指定角色已授权的资源ID列表，用于权限缓存及授权页面回显。
     *
     * @param roleId 角色ID
     * @return 资源ID列表
     */
    List<String> selectResourceIdsByRoleId(String roleId);

    /**
     * 删除指定角色的全部资源授权，通常在重新授权前调用。
     *
     * @param roleId 角色ID
     * @return 受影响行数
     */
    int deleteByRoleId(String roleId);

    /**
     * 删除指定资源的全部角色授权，用于资源下线时的级联清理。
     *
     * @param resourceId 资源ID
     * @return 受影响行数
     */
    int deleteByResourceId(String resourceId);

    /**
     * 查询绑定了指定资源的全部角色ID列表（资源维度反查），用于"菜单分配角色"对话框回显。
     *
     * @param resourceId 资源ID
     * @return 角色ID列表
     */
    List<String> selectRoleIdsByResourceId(String resourceId);

    /**
     * 判断指定角色与资源的授权关系是否存在，用于幂等性校验。
     *
     * @param roleId     角色ID
     * @param resourceId 资源ID
     * @return true-已存在授权，false-未授权
     */
    boolean existsByRoleIdAndResourceId(@Param("roleId") String roleId,
                                        @Param("resourceId") String resourceId);

    /**
     * 查询指定角色已授权的"菜单"资源ID列表（即 PT_RESOURCE.IS_MENU=1 的部分）。
     * <p>用于角色管理页面"分配菜单"对话框的回显，与接口资源（IS_MENU=0）分开取。</p>
     *
     * @param roleId 角色ID
     * @return 菜单资源ID列表
     */
    List<String> selectMenuIdsByRoleId(String roleId);

    /**
     * 删除指定角色的"菜单"绑定（仅 PT_RESOURCE.IS_MENU=1 的部分），
     * 接口资源绑定（IS_MENU=0）不动。配合 replaceMenus 全量替换菜单分配。
     *
     * @param roleId 角色ID
     * @return 受影响行数
     */
    int deleteMenuBindingsByRoleId(String roleId);

    /**
     * 删除指定角色的"接口"绑定（仅 PT_RESOURCE.ISMENU=0 的部分），菜单绑定不动。
     * <p>供显式接口权限管理使用；菜单分配不得调用本方法。</p>
     *
     * @param roleId 角色ID
     * @return 受影响行数
     */
    int deleteInterfaceBindingsByRoleId(String roleId);
}
