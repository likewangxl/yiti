package com.bank.branch.platform.auth.mapper;

import com.bank.branch.platform.auth.entity.PtRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色 Mapper 接口，操作 PT_ROLE 表。
 * <p>
 * 提供角色的基础 CRUD 及关联查询能力，分页查询采用物理分页（LIMIT/OFFSET）。
 * </p>
 */
@Mapper
public interface RoleMapper {

    /**
     * 根据角色ID查询角色信息。
     *
     * @param roleId 角色ID
     * @return 角色实体，不存在时返回 null
     */
    PtRole selectByRoleId(String roleId);

    /**
     * 根据角色编码查询角色信息，用于判断编码唯一性。
     *
     * @param roleCode 角色编码
     * @return 角色实体，不存在时返回 null
     */
    PtRole selectByRoleCode(String roleCode);

    /**
     * 分页查询角色列表，支持按关键字（角色名/编码模糊）和状态过滤。
     *
     * @param keyword      搜索关键字，模糊匹配 ROLE_CODE 和 ROLE_CHNAME，为 null 时不过滤
     * @param recordStatus 记录状态过滤，为 null 时不过滤
     * @param offset       分页偏移量（从 0 开始）
     * @param limit        每页记录数
     * @return 角色列表
     */
    List<PtRole> selectByPage(@Param("keyword") String keyword,
                              @Param("recordStatus") Integer recordStatus,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

    /**
     * 统计满足条件的角色总数，与 selectByPage 配套使用。
     *
     * @param keyword      搜索关键字
     * @param recordStatus 记录状态过滤
     * @return 总记录数
     */
    long countByPage(@Param("keyword") String keyword,
                     @Param("recordStatus") Integer recordStatus);

    /**
     * 新增角色记录。
     *
     * @param role 角色实体
     * @return 受影响行数
     */
    int insert(PtRole role);

    /**
     * 按主键（roleId）更新角色信息，使用动态 SET 仅更新非 null 字段。
     *
     * @param role 包含 roleId 及待更新字段的角色实体
     * @return 受影响行数
     */
    int updateById(PtRole role);

    /**
     * 查询指定用户已分配的角色列表，通过 PT_USER_ROLE 关联。
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    List<PtRole> selectByUserId(String userId);

    /**
     * 查询全部可用角色，用于 BizScope 矩阵构建。
     *
     * @return 全部 RECORD_STATUS = 0 的角色列表
     */
    List<PtRole> selectAll();
}
