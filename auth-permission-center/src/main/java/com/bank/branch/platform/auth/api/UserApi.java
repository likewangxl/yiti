package com.bank.branch.platform.auth.api;

import com.bank.branch.platform.auth.api.dto.UserDTO;

import java.util.List;

/**
 * 用户信息对外API
 * <p>
 * 提供用户姓名、用户信息查询等能力。
 * </p>
 */
public interface UserApi {

    /**
     * 根据工号查询用户信息
     *
     * @param empId 员工ID
     * @return 用户DTO，不存在时返回 null
     */
    UserDTO getUserByEmpId(String empId);

    /**
     * 根据工号获取用户姓名
     *
     * @param empId 员工ID
     * @return 用户姓名，不存在时返回 null
     */
    String getUserName(String empId);

    /**
     * 批量获取用户信息
     *
     * @param empIds 员工ID列表
     * @return 用户DTO列表
     */
    List<UserDTO> getUserByEmpIds(List<String> empIds);
}
