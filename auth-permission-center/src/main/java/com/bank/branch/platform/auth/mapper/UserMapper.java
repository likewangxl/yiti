package com.bank.branch.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.auth.api.dto.UserQueryReqDTO;
import com.bank.branch.platform.auth.entity.PtUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户 Mapper 接口，操作 PT_USER 表。
 * <p>
 * 仅暴露本模块内部所需的最小 SQL 操作集合，禁止在此接口中添加跨模块业务逻辑。
 * </p>
 * <p>
 * MyBatis-Plus 接入：继承 {@link BaseMapper} 后，{@code updateById(T)} 由 BaseMapper 提供
 * （动态 SET：仅更新非 null 字段，与原 XML updateById 行为一致）。
 * 自定义业务查询继续保留在本接口和 XML。
 * </p>
 */
@Mapper
public interface UserMapper extends BaseMapper<PtUser> {

    /**
     * 根据用户ID查询用户信息。
     *
     * @param userId 用户ID（工号）
     * @return 用户实体，不存在时返回 null
     */
    PtUser selectByUserId(String userId);

    /**
     * 根据机构编码查询该机构下的所有用户（G.2）
     *
     * @param orgCode 机构编码
     * @return 用户列表
     */
    List<PtUser> selectByOrgCode(String orgCode);

    /**
     * 分页查询机构下的用户（G.2）
     *
     * @param orgCode 机构编码
     * @param keyword 关键字（工号/姓名），可选
     * @param offset 偏移量
     * @param limit 每页条数
     * @return 用户列表
     */
    List<PtUser> selectOrgUsersByPage(@Param("orgCode") String orgCode,
                                      @Param("keyword") String keyword,
                                      @Param("isEnabled") Integer isEnabled,
                                      @Param("isLocked") Integer isLocked,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    /**
     * 统计机构下的用户总数（G.2）
     *
     * @param orgCode 机构编码
     * @param keyword 关键字
     * @param isEnabled 启用状态过滤（0/1），null 不过滤
     * @param isLocked 锁定状态过滤（0/1），null 不过滤
     * @return 用户总数
     */
    long countOrgUsers(@Param("orgCode") String orgCode, @Param("keyword") String keyword,
                       @Param("isEnabled") Integer isEnabled, @Param("isLocked") Integer isLocked);

    /**
     * 根据用户名（登录名）查询用户信息，用于登录认证。
     *
     * @param username 用户名
     * @return 用户实体，不存在时返回 null
     */
    PtUser selectByUsername(String username);

    List<PtUser> selectByUsernames(@Param("usernames") List<String> usernames);

    /**
     * 按 USER_ID 列表批量查询用户（仅用于 USER_ID → USERNAME 反查），单次 IN 查询。
     *
     * @param userIds USER_ID 列表（调用方保证非空、已分片）
     * @return 命中的用户实体列表
     */
    List<PtUser> selectByUserIds(@Param("userIds") List<String> userIds);

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

    /** 条件分页列表（V1.14 新增） */
    List<PtUser> selectByQuery(@Param("q") UserQueryReqDTO q,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    /** 条件总数（V1.14 新增） */
    long countByQuery(@Param("q") UserQueryReqDTO q);

    /** 用户名是否已存在（V1.14 新增） */
    int countByUsername(@Param("username") String username);

    /** 修改启用状态（V1.14 新增） */
    int updateActiveStatus(@Param("userId") String userId,
                           @Param("isEnabled") int isEnabled,
                           @Param("updateAuthor") String updateAuthor);

    /** 修改密码并刷新 PWD_UPDATE_TIME / 清零 PASS_WRONG_COUNT（V1.14 新增） */
    int updatePassword(@Param("userId") String userId,
                       @Param("pwd") String bcryptHash,
                       @Param("updateAuthor") String updateAuthor);

    /** 物理批量删除（V1.14 新增） */
    int deleteByUserIds(@Param("userIds") java.util.List<String> userIds);

    /**
     * 按关键词 OR 模糊分页查询用户（工号/登录名/中文名），供人员标签列表用。
     *
     * @param keyword 关键词（null/空 表示不过滤，返回全部）
     * @param offset  偏移量（从 0 开始）
     * @param limit   每页条数
     * @return 用户列表，按 USER_ID 升序
     */
    List<PtUser> selectByKeywordPaged(@Param("keyword") String keyword,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    /**
     * 与 selectByKeywordPaged 配套的总数统计。
     *
     * @param keyword 关键词（null/空 表示不过滤）
     * @return 总条数
     */
    long countByKeyword(@Param("keyword") String keyword);

}
