package com.bank.branch.platform.auth.api;

import com.bank.branch.platform.auth.api.dto.UserDirectoryDTO;
import com.bank.branch.platform.common.web.PageResult;

import java.util.List;

/**
 * 用户通讯录对外 API。
 *
 * <p>通讯录人员维度统一来自 {@code PT_USER}，机构信息来自
 * {@code EXT_USER_ORG + EXT_ORG_INFO}。模块化单体内其他模块通过此接口访问，
 * 不得直接依赖 auth 的 entity、mapper 或 service。</p>
 */
public interface UserDirectoryApi {

    /**
     * 分页查询在职用户。
     *
     * @param keyword 关键字（用户 ID、登录名、姓名、电话或邮箱），可为空
     * @param orgCode 机构编码，可为空
     * @param pageNo 页码，从 1 开始
     * @param pageSize 每页条数，服务端限制最大 100
     * @return 在职用户分页结果
     */
    PageResult<UserDirectoryDTO> pageActiveUsers(String keyword, String orgCode,
                                                 int pageNo, int pageSize);

    /**
     * 按 USER_ID 查询单个用户。
     *
     * @param userId PT_USER.USER_ID
     * @return 用户通讯录视图，不存在时返回 null
     */
    UserDirectoryDTO getEmployee(String userId);

    /**
     * 批量按 USER_ID 查询用户。
     *
     * @param userIds PT_USER.USER_ID 列表
     * @return 命中的用户，按入参顺序返回
     */
    List<UserDirectoryDTO> getEmployeesByIds(List<String> userIds);

    /**
     * 搜索在职用户。
     *
     * @param keyword 姓名、登录名、用户 ID、电话或邮箱关键字
     * @param limit 最大返回条数
     * @return 命中的用户列表
     */
    List<UserDirectoryDTO> searchEmployees(String keyword, int limit);

    /**
     * 更新当前登录用户自己的电话和邮箱。
     * <p>用户 ID 从当前认证上下文取得，调用方不能指定其他用户。</p>
     *
     * @param mobile 电话号码，可为空以清空
     * @param email 邮箱，可为空以清空
     * @return 更新后的用户通讯录视图
     */
    UserDirectoryDTO updateCurrentUserContact(String mobile, String email);

    /** 语义别名：按 USER_ID 查询单个用户。 */
    default UserDirectoryDTO getById(String userId) {
        return getEmployee(userId);
    }

    /** 语义别名：批量按 USER_ID 查询用户。 */
    default List<UserDirectoryDTO> getByIds(List<String> userIds) {
        return getEmployeesByIds(userIds);
    }

    /** 语义别名：搜索在职用户。 */
    default List<UserDirectoryDTO> search(String keyword, int limit) {
        return searchEmployees(keyword, limit);
    }
}
