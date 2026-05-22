package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.RoleRespDTO;
import com.bank.branch.platform.auth.entity.PtRole;
import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.auth.mapper.RoleMapper;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 角色管理服务
 * <p>
 * 提供角色的 CRUD 操作，负责角色编码唯一性校验、逻辑删除及缓存失效管理。
 * 所有写操作加 @Transactional，保证数据库操作与缓存失效的原子性边界。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionCacheService cacheService;

    /**
     * 根据角色ID查询角色DTO，角色不存在时抛出 AUTH-40401。
     *
     * @param roleId 角色ID
     * @return 角色响应DTO
     * @throws BizException AUTH-40401 当角色不存在时
     */
    public RoleRespDTO getById(String roleId) {
        log.debug("[RoleService.getById] roleId={}", roleId);
        PtRole role = getEntityById(roleId);
        return toDto(role);
    }

    /**
     * 分页查询角色列表，支持按关键字（角色名/编码模糊匹配）和状态过滤。
     *
     * @param keyword      搜索关键字，null 时不过滤
     * @param recordStatus 记录状态，null 时不过滤
     * @param pageNo       页码（从 1 开始）
     * @param pageSize     每页记录数
     * @return 分页结果
     */
    public PageResult<RoleRespDTO> listByPage(String keyword, Integer recordStatus, int pageNo, int pageSize) {
        log.debug("[RoleService.listByPage] keyword={}, recordStatus={}, pageNo={}, pageSize={}", keyword, recordStatus, pageNo, pageSize);
        int offset = (pageNo - 1) * pageSize;
        List<PtRole> roles = roleMapper.selectByPage(keyword, recordStatus, offset, pageSize);
        long total = roleMapper.countByPage(keyword, recordStatus);
        List<RoleRespDTO> records = roles.stream().map(this::toDto).collect(Collectors.toList());
        // 按角色 ID 填 userCount（每个角色单独 COUNT，分页本身已限 size，N 次查询可控）
        for (RoleRespDTO dto : records) {
            dto.setUserCount((int) userRoleMapper.countByRoleId(dto.getRoleId()));
        }
        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * 新增角色。
     * <p>
     * 生成角色ID格式：R_ + UUID前8位大写。
     * 校验 roleCode 唯一，重复时抛出 AUTH-40901。
     * </p>
     *
     * @param roleCode    角色编码，全局唯一
     * @param roleChName  角色中文名
     * @param remark      备注，可为 null
     * @return 新创建的角色DTO
     * @throws BizException AUTH-40901 当角色编码重复时
     */
    @Transactional
    public RoleRespDTO createRole(String roleCode, String roleChName, String remark) {
        log.info("[RoleService.createRole] roleCode={}, roleChName={}", roleCode, roleChName);
        // 前端新增时不输入 roleCode，由后端按 R_ + UUID 8 位大写自动生成
        if (roleCode == null || roleCode.isBlank()) {
            roleCode = "R_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        }
        // 校验角色编码唯一性 — 防止同名角色导致权限混乱
        if (roleMapper.selectByRoleCode(roleCode) != null) {
            throw new BizException(AuthErrorCode.ROLE_CODE_DUPLICATE.getCode(),
                    AuthErrorCode.ROLE_CODE_DUPLICATE.getMessage());
        }
        String roleId = "R_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        PtRole role = new PtRole();
        role.setRoleId(roleId);
        role.setRoleCode(roleCode);
        role.setRoleChName(roleChName);
        role.setRemark(remark);
        role.setRecordStatus(0);
        role.setSysCode("PLATFORM");
        role.setCreateTime(LocalDateTime.now());
        role.setUpdateTime(LocalDateTime.now());
        roleMapper.insert(role);
        log.info("[RoleService.createRole] 角色创建成功 roleId={}", roleId);
        return toDto(role);
    }

    /**
     * 更新角色中文名和备注，不允许修改 roleCode（避免权限策略混乱）。
     * 角色不存在时抛出 AUTH-40401。
     *
     * @param roleId     角色ID
     * @param roleChName 新的角色中文名
     * @param remark     新的备注，可为 null
     * @return 更新后的角色DTO
     * @throws BizException AUTH-40401 当角色不存在时
     */
    @Transactional
    public RoleRespDTO updateRole(String roleId, String roleChName, String remark, Integer recordStatus) {
        log.info("[RoleService.updateRole] roleId={}, roleChName={}, recordStatus={}", roleId, roleChName, recordStatus);
        PtRole existing = getEntityById(roleId);
        existing.setRoleChName(roleChName);
        existing.setRemark(remark);
        // null 表示不修改状态（向后兼容），非 null 时才覆盖
        if (recordStatus != null) {
            existing.setRecordStatus(recordStatus);
        }
        existing.setUpdateTime(LocalDateTime.now());
        roleMapper.updateById(existing);
        return toDto(existing);
    }

    /**
     * 逻辑删除角色：将 RECORD_STATUS 设为 1，并清除该角色相关缓存。
     * 角色不存在时抛出 AUTH-40401。
     *
     * @param roleId 角色ID
     * @param reason 删除原因（供审计使用）
     * @throws BizException AUTH-40401 当角色不存在时
     */
    @Transactional
    public void deleteRole(String roleId, String reason) {
        log.info("[RoleService.deleteRole] roleId={}, reason={}", roleId, reason);
        PtRole existing = getEntityById(roleId);
        // 逻辑删除：保留记录，标记为不可用，避免历史数据孤立
        existing.setRecordStatus(1);
        existing.setUpdateTime(LocalDateTime.now());
        roleMapper.updateById(existing);
        // 清除与该角色关联的所有权限缓存，保证 Fail Close
        cacheService.evictRoleResourceCache(roleId);
        cacheService.evictBizScopeCache(roleId);
        log.info("[RoleService.deleteRole] 角色已逻辑删除 roleId={}", roleId);
    }

    /**
     * 内部方法：按角色ID加载实体，不存在时统一抛出 AUTH-40401。
     * 供本模块其他 Service 复用，避免重复的 null 判断逻辑。
     *
     * @param roleId 角色ID
     * @return PtRole 实体
     * @throws BizException AUTH-40401 当角色不存在时
     */
    public PtRole getEntityById(String roleId) {
        PtRole role = roleMapper.selectByRoleId(roleId);
        if (role == null) {
            throw new BizException(AuthErrorCode.ROLE_NOT_FOUND.getCode(),
                    AuthErrorCode.ROLE_NOT_FOUND.getMessage());
        }
        return role;
    }

    /**
     * 将 PtRole 实体转换为 RoleRespDTO。
     * 所有字段直接映射，userCount 默认置 0（按需由调用方填充）。
     *
     * @param role 角色实体
     * @return 角色响应DTO
     */
    private RoleRespDTO toDto(PtRole role) {
        RoleRespDTO dto = new RoleRespDTO();
        dto.setRoleId(role.getRoleId());
        dto.setRoleCode(role.getRoleCode());
        dto.setRoleChName(role.getRoleChName());
        dto.setRecordStatus(role.getRecordStatus());
        dto.setSysCode(role.getSysCode());
        dto.setCreateTime(role.getCreateTime());
        dto.setUpdateTime(role.getUpdateTime());
        dto.setRemark(role.getRemark());
        return dto;
    }
}
