package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.api.dto.RoleUserRespDTO;
import com.bank.branch.platform.auth.api.event.PermissionCacheInvalidatedEvent;
import com.bank.branch.platform.auth.entity.PtRole;
import com.bank.branch.platform.auth.entity.PtUser;
import com.bank.branch.platform.auth.entity.PtUserRole;
import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.auth.mapper.RoleMapper;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户角色绑定管理服务
 * <p>
 * 负责用户与角色的绑定/解绑操作，所有写操作后须清除用户角色缓存并发布
 * PermissionCacheInvalidatedEvent 事件，通知其他节点同步失效。
 * bindRoles 实现幂等语义：已绑定的角色跳过，仅插入新增绑定。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRoleService {

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final UserMapper userMapper;
    private final PermissionCacheService cacheService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 为用户批量绑定角色（幂等操作）。
     * <p>
     * 校验用户存在性 → 查询已有绑定 → 仅插入尚未绑定的角色 → 清除缓存 → 发布事件。
     * 不存在的角色 ID 会被跳过（roleMapper 返回 null），保证接口容错性。
     * </p>
     *
     * @param userId  用户ID（工号）
     * @param roleIds 待绑定角色ID列表
     * @param reason  绑定原因（供审计使用）
     * @throws BizException AUTH-40403 当用户不存在时
     */
    @Transactional
    public void bindRoles(String userId, List<String> roleIds, String reason) {
        log.info("[UserRoleService.bindRoles] userId={}, roleIds={}, reason={}", userId, roleIds, reason);
        // 校验用户存在性，防止为幽灵用户分配权限
        PtUser user = userMapper.selectByUserId(userId);
        if (user == null) {
            throw new BizException(AuthErrorCode.USER_NOT_FOUND.getCode(),
                    AuthErrorCode.USER_NOT_FOUND.getMessage());
        }
        // 查询用户当前已绑定的角色ID集合，用于幂等去重
        List<String> existingRoleIds = userRoleMapper.selectRoleIdsByUserId(userId);
        Set<String> existingSet = existingRoleIds == null ? Set.of()
                : existingRoleIds.stream().collect(Collectors.toSet());

        boolean hasNewBinding = false;
        for (String roleId : roleIds) {
            // 校验角色存在性，不存在则跳过（容错：批量绑定时部分角色可能已被删除）
            PtRole role = roleMapper.selectByRoleId(roleId);
            if (role == null) {
                log.warn("[UserRoleService.bindRoles] 角色不存在，跳过 roleId={}", roleId);
                continue;
            }
            // 幂等：已绑定的角色跳过，避免重复插入违反唯一约束（先校验角色，再检查重复，保证容错顺序一致）
            if (existingSet.contains(roleId)) {
                log.debug("[UserRoleService.bindRoles] 角色已绑定，跳过 userId={}, roleId={}", userId, roleId);
                continue;
            }
            PtUserRole userRole = new PtUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRole.setDefaultAssign(0);
            userRole.setInheritAssign(0);
            userRole.setGroupAssing(0);
            userRole.setCreateTime(LocalDateTime.now());
            userRoleMapper.insert(userRole);
            hasNewBinding = true;
        }

        if (hasNewBinding) {
            // 有新绑定时才失效缓存，减少不必要的 Redis 操作
            cacheService.evictUserRolesCache(userId);
            publishCacheInvalidatedEvent(userId, reason);
            log.info("[UserRoleService.bindRoles] 角色绑定完成，缓存已清除 userId={}", userId);
        }
    }

    /**
     * 解绑用户的指定角色，并清除缓存、发布事件。
     *
     * @param userId 用户ID（工号）
     * @param roleId 角色ID
     * @param reason 解绑原因（供审计使用）
     */
    @Transactional
    public void unbindRole(String userId, String roleId, String reason) {
        log.info("[UserRoleService.unbindRole] userId={}, roleId={}, reason={}", userId, roleId, reason);
        userRoleMapper.deleteByUserIdAndRoleId(userId, roleId);
        // 解绑后立即清除缓存，保证权限 Fail Close
        cacheService.evictUserRolesCache(userId);
        publishCacheInvalidatedEvent(userId, reason);
        log.info("[UserRoleService.unbindRole] 角色解绑完成 userId={}, roleId={}", userId, roleId);
    }

    /**
     * 分页查询指定角色下已绑定的用户列表（含机构信息和绑定时间）。
     *
     * @param roleId   角色ID
     * @param keyword  工号/姓名关键字（可为 null）
     * @param pageNo   页码（从1开始）
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public PageResult<RoleUserRespDTO> listRoleUsers(String roleId, String keyword, int pageNo, int pageSize) {
        int offset = (pageNo - 1) * pageSize;
        List<RoleUserRespDTO> items = userRoleMapper.selectRoleUserDetailsByRoleId(roleId, keyword, offset, pageSize);
        long total = userRoleMapper.countUsersByRoleId(roleId, keyword);
        return PageResult.of(pageNo, pageSize, total, items);
    }

    /**
     * 查询用户已绑定的角色简要信息列表。
     *
     * @param userId 用户ID（工号）
     * @return RoleSimpleDTO 列表
     */
    public List<RoleSimpleDTO> getRolesByUserId(String userId) {
        log.debug("[UserRoleService.getRolesByUserId] userId={}", userId);
        List<PtRole> roles = userRoleMapper.selectRolesByUserId(userId);
        return roles.stream().map(this::toSimpleDto).collect(Collectors.toList());
    }

    /**
     * 构建并发布权限缓存失效事件，通知集群内其他节点清除相关缓存。
     *
     * @param userId 受影响的用户ID
     * @param reason 变更原因
     */
    private void publishCacheInvalidatedEvent(String userId, String reason) {
        PermissionCacheInvalidatedEvent event = new PermissionCacheInvalidatedEvent();
        event.setChangeType("USER_ROLE");
        event.setAffectedUserIds(Set.of(userId));
        event.setOperator(userId); // placeholder：正式环境应从 SecurityContext 获取操作人
        event.setReason(reason);
        event.setEventId("evt_" + System.currentTimeMillis());
        event.setOccurredAt(System.currentTimeMillis());
        eventPublisher.publishEvent(event);
    }

    /**
     * 将 PtRole 实体转换为 RoleSimpleDTO（仅包含 ID、编码、中文名三个核心字段）。
     *
     * @param role 角色实体
     * @return RoleSimpleDTO
     */
    private RoleSimpleDTO toSimpleDto(PtRole role) {
        RoleSimpleDTO dto = new RoleSimpleDTO();
        dto.setRoleId(role.getRoleId());
        dto.setRoleCode(role.getRoleCode());
        dto.setRoleChName(role.getRoleChName());
        return dto;
    }
}
