package com.bank.branch.platform.auth.api;

import com.bank.branch.platform.auth.api.dto.UserDTO;

import java.util.List;
import java.util.Set;

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

    /**
     * 查询任意员工的角色编码集合（roleCode，如 R_RM / R_ADMIN / R_BACK_TECH）。
     * <p>
     * 与 {@code CurrentUserApi.getCurrentRoleCodes()} 区别：本方法可查任意员工，需要 DB IO。
     * 跨模块业务校验场景使用，如客户认领转交时校验接收人角色（CUST-40306）。
     * empId 为空或不存在时返回空集合，永不返回 null。
     * </p>
     * <p>
     * <b>Performance note (P1C 2026-04-29)</b>：本方法当前 <b>不走缓存</b>，
     * 每次调用执行 PT_USER_ROLE JOIN PT_ROLE 查询。auth 模块既有 cache `auth:user-roles:{empId}`
     * 缓存的是 roleIds（非 roleCodes），语义不同无法复用。
     * 高频调用场景请自行做应用层缓存或后续 follow-up 加 PermissionCacheService 支持。
     * </p>
     *
     * @param empId 员工ID（工号）
     * @return 角色编码集合（roleCode），从 PT_USER_ROLE 联 PT_ROLE 查得；员工无角色或不存在时为空集
     */
    Set<String> getUserRoleCodes(String empId);
}
