package com.bank.branch.platform.auth.api;

import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;

import java.util.List;
import java.util.Map;
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

    /**
     * 按 empId 计算工作流候选组标识集合（带前缀：{@code ROLE:{roleCode}} / {@code USER:{empId}} / {@code ORG:{mainOrgCode}}）。
     * <p>
     * 与 {@code CurrentUserApi.getCurrentCandidateGroupKeys()} 的区别：后者读登录态 ThreadLocal，
     * 只能取「当前登录用户」的候选组；本方法按传入 empId 查库实时计算，<b>不依赖会话上下文</b>，
     * 供 SOAP 网关 / callpu 等<b>无登录态</b>链路按指定员工查询工作流待办的候选可见性使用
     * （否则会触发 AUTH-40105「未登录或会话已过期」）。
     * 口径与 {@code AuthService.login} 构建 candidateGroupKeys 的逻辑保持一致：
     * 每个 roleCode 加 {@code ROLE:} 前缀、固定加一项 {@code USER:{empId}}、有主机构时加 {@code ORG:{mainOrgCode}}。
     * empId 为空时返回空集合，永不返回 null。
     * </p>
     *
     * @param empId 员工ID（工号）
     * @return 候选组标识集合（ROLE:/USER:/ORG: 前缀）；员工无角色/无主机构时仅含可推导项
     */
    Set<String> getCandidateGroupKeys(String empId);

    /**
     * 按 roleCode 查所有启用员工 ID。
     * <p>用于 workflow / portal 等模块把候选组 roleCode 展开成员工列表（如发审批通知）。
     * 仅返启用 (PT_USER.ISENABLED=0) + 未被逻辑删除的角色 (PT_ROLE.RECORD_STATUS=0)。</p>
     *
     * @param roleCode 角色编码（如 BRANCH_HEAD）
     * @return 员工 ID 列表，roleCode 为空或无人时返空 List
     */
    List<String> getEmpIdsByRoleCode(String roleCode);

    /**
     * 按 roleCode + orgCode 查同机构启用员工 ID。
     * <p>用于审批候选人按发起人机构过滤（如机构负责人必须与发起人同机构）。
     * 查询逻辑：PT_USER_ROLE JOIN PT_ROLE JOIN EXT_USER_ORG，
     * 仅返启用 (ISENABLED=0) + 角色有效 (RECORD_STATUS=0) + 机构匹配。</p>
     *
     * @param roleCode 角色编码
     * @param orgCode  机构编码
     * @return 员工 ID 列表，无人时返空 List
     */
    List<String> getEmpIdsByRoleCodeAndOrg(String roleCode, String orgCode);

    /**
     * 按 orgCode 查该机构下全部用户工号（任一角色），供审批人「机构角色」不选角色场景。
     *
     * @param orgCode 机构编码
     * @return 员工 ID 列表，无人或入参空时返空 List
     */
    List<String> getEmpIdsByOrg(String orgCode);

    /**
     * 按 username 批量查用户信息。
     * <p>用于数据湖 Allocater_Id（对应 PT_USER.USERNAME）关联查员工姓名和机构。</p>
     *
     * @param usernames 用户名列表
     * @return 用户DTO列表
     */
    List<UserDTO> getUsersByUsernames(List<String> usernames);

    /**
     * 批量过滤出「确实存在」的用户名（工号），用于大批量存在性校验.
     *
     * <p>与 {@link #getUsersByUsernames(List)} 不同：本方法**只做存在性判断**，单次/分片 IN 查询，
     * 不装配机构等 DTO 信息，避免逐人 N+1（导入 5 万行时 getUsersByUsernames 会触发约 15 万次查询）。
     *
     * @param usernames 待校验用户名列表（null/空 → 返回空）
     * @return 其中在 PT_USER 中存在的用户名子集（去重，顺序不保证）
     */
    List<String> filterExistingUsernames(List<String> usernames);

    /**
     * 按关键词分页查询用户（工号/登录名/中文名 OR 模糊），供 performance 人员标签列表用。
     *
     * @param keyword  关键词（null/空 不过滤）
     * @param pageNo   页码（从 1 开始，&lt;1 归一为 1）
     * @param pageSize 每页条数（&lt;1 归一为 20，&gt;100 截断为 100）
     * @return 分页用户（empId/username/displayName 已装配，mainOrg 不填充）
     */
    PageResult<UserDTO> pageUsers(String keyword, int pageNo, int pageSize);

    /**
     * 批量查询多个用户的角色简要列表，避免逐用户 N+1。
     *
     * @param userIds 用户ID（工号）列表
     * @return Map&lt;userId, 角色列表&gt;；入参为空时返回空 Map，无角色的 userId 不在 Map 中
     */
    Map<String, List<RoleSimpleDTO>> getRolesByUserIds(List<String> userIds);
}
