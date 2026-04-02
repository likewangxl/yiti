package com.bank.branch.platform.auth.mapper;

import com.bank.branch.platform.auth.entity.PtRoleBizScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色业务范围 Mapper 接口，操作 PT_ROLE_BIZ_SCOPE 表。
 * <p>
 * 数据权限（DataScope）是平台细粒度权限控制的核心。
 * selectAll() 用于启动时将全量配置加载到缓存，后续查询直接走缓存，
 * 避免每次鉴权都访问数据库。
 * </p>
 */
@Mapper
public interface RoleBizScopeMapper {

    /**
     * 查询指定角色的全部业务范围配置。
     *
     * @param roleId 角色ID
     * @return 业务范围列表
     */
    List<PtRoleBizScope> selectByRoleId(String roleId);

    /**
     * 查询指定角色在特定业务类型下的数据范围配置（精确匹配）。
     * 利用表上的唯一索引 uk_pt_role_biz_scope_role_biz 实现高效查询。
     *
     * @param roleId  角色ID
     * @param bizType 业务类型
     * @return 业务范围实体，不存在时返回 null
     */
    PtRoleBizScope selectByRoleIdAndBizType(@Param("roleId") String roleId,
                                            @Param("bizType") String bizType);

    /**
     * 根据主键ID查询业务范围配置。
     *
     * @param id 主键ID
     * @return 业务范围实体，不存在时返回 null
     */
    PtRoleBizScope selectById(String id);

    /**
     * 分页查询业务范围配置，支持按角色ID和业务类型过滤。
     *
     * @param roleId  角色ID 过滤条件，为 null 时不过滤
     * @param bizType 业务类型过滤条件，为 null 时不过滤
     * @param offset  分页偏移量（从 0 开始）
     * @param limit   每页记录数
     * @return 业务范围列表
     */
    List<PtRoleBizScope> selectByPage(@Param("roleId") String roleId,
                                      @Param("bizType") String bizType,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    /**
     * 统计满足条件的业务范围配置总数，与 selectByPage 配套使用。
     *
     * @param roleId  角色ID 过滤条件
     * @param bizType 业务类型过滤条件
     * @return 总记录数
     */
    long countByPage(@Param("roleId") String roleId,
                     @Param("bizType") String bizType);

    /**
     * 查询全量业务范围配置，用于系统启动时加载到 Redis 缓存。
     *
     * @return 全部有效的业务范围配置列表
     */
    List<PtRoleBizScope> selectAll();

    /**
     * 新增业务范围配置记录。
     *
     * @param scope 业务范围实体
     * @return 受影响行数
     */
    int insert(PtRoleBizScope scope);

    /**
     * 按主键（id）更新业务范围配置，使用动态 SET 仅更新非 null 字段。
     *
     * @param scope 包含 id 及待更新字段的业务范围实体
     * @return 受影响行数
     */
    int updateById(PtRoleBizScope scope);

    /**
     * 按主键删除业务范围配置记录。
     *
     * @param id 主键ID
     * @return 受影响行数
     */
    int deleteById(String id);

    /**
     * 删除指定角色的全部业务范围配置，通常在角色删除前的级联清理中调用。
     *
     * @param roleId 角色ID
     * @return 受影响行数
     */
    int deleteByRoleId(String roleId);
}
