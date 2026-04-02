package com.bank.branch.platform.auth.mapper;

import com.bank.branch.platform.auth.entity.PtUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户 Mapper 接口，操作 PT_USER 表。
 * <p>
 * 仅暴露本模块内部所需的最小 SQL 操作集合，禁止在此接口中添加跨模块业务逻辑。
 * </p>
 */
@Mapper
public interface UserMapper {

    /**
     * 根据用户ID查询用户信息。
     *
     * @param userId 用户ID（工号）
     * @return 用户实体，不存在时返回 null
     */
    PtUser selectByUserId(String userId);

    /**
     * 根据用户名（登录名）查询用户信息，用于登录认证。
     *
     * @param username 用户名
     * @return 用户实体，不存在时返回 null
     */
    PtUser selectByUsername(String username);

    /**
     * 更新指定用户的密码错误计数。
     * 登录失败时递增，登录成功后重置为 0。
     *
     * @param userId 用户ID
     * @param count  新的错误次数值
     * @return 受影响行数
     */
    int updatePassWrongCount(@Param("userId") String userId, @Param("count") int count);

    /**
     * 更新指定用户的锁定状态。
     * 密码错误超限时锁定（locked=1），管理员解锁时重置（locked=0）。
     *
     * @param userId 用户ID
     * @param locked 锁定状态：0-未锁定，1-已锁定
     * @return 受影响行数
     */
    int updateLockedStatus(@Param("userId") String userId, @Param("locked") int locked);

    /**
     * 按主键（userId）更新用户信息，使用动态 SET 仅更新非 null 字段。
     *
     * @param user 包含 userId 及待更新字段的用户实体
     * @return 受影响行数
     */
    int updateById(PtUser user);
}
