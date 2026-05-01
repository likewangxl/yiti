package com.bank.branch.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.auth.entity.PtRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色 Mapper 接口，操作 PT_ROLE 表。
 * <p>
 * 提供角色的基础 CRUD 及关联查询能力，分页查询采用物理分页（LIMIT/OFFSET）。
 * </p>
 * <p>
 * MyBatis-Plus 接入：继承 {@link BaseMapper} 后，{@code insert(T)} /
 * {@code updateById(T)} 由 BaseMapper 提供。
 * 自定义业务查询（selectByRoleId、selectByRoleCode、selectByPage 等）继续保留在本接口和 XML。
 * </p>
 */
@Mapper
public interface RoleMapper extends BaseMapper<PtRole> {

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
