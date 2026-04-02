package com.bank.branch.platform.auth.mapper;

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
 */
@Mapper
public interface RoleResourceMapper {

    /**
     * 查询指定角色已授权的资源ID列表，用于权限缓存及授权页面回显。
     *
     * @param roleId 角色ID
     * @return 资源ID列表
     */
    List<String> selectResourceIdsByRoleId(String roleId);

    /**
     * 新增角色资源授权记录。
     *
     * @param roleResource 角色资源关联实体
     * @return 受影响行数
     */
    int insert(PtRoleResource roleResource);

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
     * 判断指定角色与资源的授权关系是否存在，用于幂等性校验。
     *
     * @param roleId     角色ID
     * @param resourceId 资源ID
     * @return true-已存在授权，false-未授权
     */
    boolean existsByRoleIdAndResourceId(@Param("roleId") String roleId,
                                        @Param("resourceId") String resourceId);
}
