package com.bank.branch.platform.auth.mapper;

import com.bank.branch.platform.auth.api.dto.RoleUserRespDTO;
import com.bank.branch.platform.auth.entity.PtRole;
import com.bank.branch.platform.auth.entity.PtUser;
import com.bank.branch.platform.auth.entity.PtUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户角色关联 Mapper 接口，操作 PT_USER_ROLE 表。
 * <p>
 * 该表维护用户与角色的多对多关系，提供双向查询（用户→角色、角色→用户）及计数统计能力。
 * </p>
 */
@Mapper
public interface UserRoleMapper {

    /**
     * 查询指定用户已分配的角色完整信息列表。
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    List<PtRole> selectRolesByUserId(String userId);

    /**
     * 查询指定用户已分配的角色ID列表，用于权限缓存构建（只需 ID，避免全量加载）。
     *
     * @param userId 用户ID
     * @return 角色ID列表
     */
    List<String> selectRoleIdsByUserId(String userId);

    /**
     * 新增用户角色关联记录。
     *
     * @param userRole 用户角色关联实体
     * @return 受影响行数
     */
    int insert(PtUserRole userRole);

    /**
     * 删除指定用户与角色的关联关系。
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 受影响行数
     */
    int deleteByUserIdAndRoleId(@Param("userId") String userId,
                                @Param("roleId") String roleId);

    /**
     * 统计指定角色下关联的用户数量，用于角色删除前校验（有用户关联则不允许删除）。
     *
     * @param roleId 角色ID
     * @return 用户数量
     */
    long countByRoleId(String roleId);

    /**
     * 分页查询指定角色下已关联的用户列表，支持关键字过滤。
     *
     * @param roleId   角色ID
     * @param keyword  搜索关键字，模糊匹配 USERNAME/USERCHNNAME，为 null 时不过滤
     * @param offset   分页偏移量（从 0 开始）
     * @param limit    每页记录数
     * @return 用户列表
     */
    List<PtUser> selectUsersByRoleId(@Param("roleId") String roleId,
                                     @Param("keyword") String keyword,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    /**
     * 统计指定角色下满足关键字条件的用户总数，与 selectUsersByRoleId 配套使用。
     *
     * @param roleId  角色ID
     * @param keyword 搜索关键字
     * @return 用户总数
     */
    long countUsersByRoleId(@Param("roleId") String roleId,
                            @Param("keyword") String keyword);

    /**
     * 分页查询角色下用户详情（含机构信息和绑定时间），用于 B.5 接口。
     *
     * @param roleId   角色ID
     * @param keyword  关键字（工号/姓名）
     * @param offset   偏移量
     * @param limit    每页条数
     * @return 包含机构信息和绑定时间的用户列表
     */
    List<RoleUserRespDTO> selectRoleUserDetailsByRoleId(@Param("roleId") String roleId,
                                                        @Param("keyword") String keyword,
                                                        @Param("offset") int offset,
                                                        @Param("limit") int limit);
}
