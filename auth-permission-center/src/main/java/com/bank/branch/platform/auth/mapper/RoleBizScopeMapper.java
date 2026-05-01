package com.bank.branch.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
 * <p>
 * MyBatis-Plus 接入：继承 {@link BaseMapper} 后，{@code insert(T)} /
 * {@code selectById(Serializable)} / {@code updateById(T)} / {@code deleteById(Serializable)}
 * 由 BaseMapper 提供（动态 SET 行为：FieldStrategy.NOT_NULL，与原 XML updateById 一致）。
 * 自定义 SQL（按业务字段查询、自定义分页、级联删除）继续保留在本接口和 XML。
 * </p>
 */
@Mapper
public interface RoleBizScopeMapper extends BaseMapper<PtRoleBizScope> {

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
     * 仅返回 RECORD_STATUS = 0（可用）的记录。
     *
     * @return 全部有效的业务范围配置列表
     */
    List<PtRoleBizScope> selectAll();

    /**
     * 删除指定角色的全部业务范围配置，通常在角色删除前的级联清理中调用。
     *
     * @param roleId 角色ID
     * @return 受影响行数
     */
    int deleteByRoleId(String roleId);
}
