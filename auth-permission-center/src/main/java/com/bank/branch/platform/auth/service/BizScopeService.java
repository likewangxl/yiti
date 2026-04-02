package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.BizScopeRespDTO;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.auth.api.event.PermissionCacheInvalidatedEvent;
import com.bank.branch.platform.auth.entity.PtRole;
import com.bank.branch.platform.auth.entity.PtRoleBizScope;
import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.auth.mapper.RoleBizScopeMapper;
import com.bank.branch.platform.auth.mapper.RoleMapper;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.common.web.exception.PermissionDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * BizType 数据范围服务
 * 负责多角色并集策略的数据范围解析、写权限校验、BizScope 配置管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizScopeService {

    /** DataScopeType 并集优先级（越大优先级越高） */
    private static final Map<DataScopeType, Integer> SCOPE_PRIORITY = Map.of(
        DataScopeType.ALL, 6,
        DataScopeType.ORG_SUBTREE, 5,
        DataScopeType.ORG, 4,
        DataScopeType.SELF_CREATED, 3,
        DataScopeType.SELF, 3,
        DataScopeType.SELF_ASSIGNED, 3,
        DataScopeType.WORKFLOW_PARTICIPANT, 2
    );

    private final RoleBizScopeMapper roleBizScopeMapper;
    private final RoleMapper roleMapper;
    private final PermissionCacheService cacheService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 解析用户对指定 BizType 的最终数据范围（多角色取并集）
     * 合并策略：ALL > ORG_SUBTREE > ORG > SELF_CREATED / SELF / SELF_ASSIGNED / WORKFLOW_PARTICIPANT
     *
     * @param empId   用户工号
     * @param bizType 业务类型
     * @return 合并后的数据范围类型
     * @throws PermissionDeniedException 用户角色未配置该 BizType 的数据范围时
     */
    public DataScopeType resolveScope(String empId, BizType bizType) {
        Set<String> roleIds = cacheService.getRoleIdsByEmpId(empId);
        DataScopeType result = null;
        int maxPriority = -1;

        for (String roleId : roleIds) {
            List<PtRoleBizScope> scopes = cacheService.getBizScopesByRoleId(roleId);
            for (PtRoleBizScope scope : scopes) {
                if (bizType.name().equals(scope.getBizType()) && scope.getRecordStatus() == 0) {
                    DataScopeType type = DataScopeType.valueOf(scope.getDataScope());
                    int priority = SCOPE_PRIORITY.getOrDefault(type, 0);
                    if (priority > maxPriority) {
                        maxPriority = priority;
                        result = type;
                    }
                }
            }
        }

        if (result == null) {
            throw new PermissionDeniedException(
                AuthErrorCode.BIZ_TYPE_NOT_CONFIGURED.getCode(),
                "用户角色未配置 " + bizType.name() + " 的数据范围");
        }
        return result;
    }

    /**
     * 构建完整的数据范围上下文
     * 上下文将被放入 ThreadLocal 供 DAO 层拼接 SQL
     *
     * @param empId     用户工号
     * @param bizType   业务类型
     * @param action    业务动作
     * @param orgCode   用户主机构编码
     * @param orgSubtreeCodes 机构子树（ORG_SUBTREE 时需传入）
     * @return 数据范围上下文
     */
    public DataScopeContext buildScopeContext(String empId, BizType bizType, BizAction action,
                                              String orgCode, Set<String> orgSubtreeCodes) {
        DataScopeType scopeType = resolveScope(empId, bizType);
        return new DataScopeContext(scopeType, empId, orgCode, orgSubtreeCodes, bizType, action);
    }

    /**
     * 校验写操作权限（基于实体归属）
     *
     * @param empId            用户工号
     * @param bizType          业务类型
     * @param entityOwnerOrgId 实体归属机构
     * @param entityCreatedBy  实体创建人
     * @return true=有写权限
     */
    public boolean checkWritePermission(String empId, BizType bizType,
                                        String entityOwnerOrgId, String entityCreatedBy) {
        DataScopeType scopeType;
        try {
            scopeType = resolveScope(empId, bizType);
        } catch (PermissionDeniedException e) {
            return false;
        }

        return switch (scopeType) {
            case ALL -> true;
            case SELF_CREATED -> empId.equals(entityCreatedBy);
            // ORG/ORG_SUBTREE 需要调用方传入机构信息，此处简化为 false（由 BizScopeFacade 处理完整逻辑）
            default -> false;
        };
    }

    /**
     * 获取用户所有 BizType 的数据范围映射（多角色并集后）
     *
     * @param empId 用户工号
     * @return BizType -> DataScopeType 映射
     */
    public Map<BizType, DataScopeType> getUserBizScopes(String empId) {
        Set<String> roleIds = cacheService.getRoleIdsByEmpId(empId);
        // 按优先级合并：key=BizType, value=最高优先级的DataScopeType
        Map<BizType, Integer> priorityMap = new HashMap<>();
        Map<BizType, DataScopeType> result = new HashMap<>();

        for (String roleId : roleIds) {
            List<PtRoleBizScope> scopes = cacheService.getBizScopesByRoleId(roleId);
            for (PtRoleBizScope scope : scopes) {
                if (scope.getRecordStatus() != 0) continue;
                try {
                    BizType bizType = BizType.valueOf(scope.getBizType());
                    DataScopeType dataScope = DataScopeType.valueOf(scope.getDataScope());
                    int priority = SCOPE_PRIORITY.getOrDefault(dataScope, 0);
                    if (priority > priorityMap.getOrDefault(bizType, -1)) {
                        priorityMap.put(bizType, priority);
                        result.put(bizType, dataScope);
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("[BizScopeService] 无效枚举值: bizType={}, dataScope={}", scope.getBizType(), scope.getDataScope());
                }
            }
        }
        return result;
    }

    /**
     * 分页查询 BizScope 配置列表
     *
     * @param roleId   角色ID过滤（可为null）
     * @param bizType  BizType过滤（可为null）
     * @param pageNo   页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public PageResult<BizScopeRespDTO> listByPage(String roleId, String bizType, int pageNo, int pageSize) {
        int offset = (pageNo - 1) * pageSize;
        List<PtRoleBizScope> items = roleBizScopeMapper.selectByPage(roleId, bizType, offset, pageSize);
        long total = roleBizScopeMapper.countByPage(roleId, bizType);
        List<BizScopeRespDTO> dtos = items.stream().map(this::toDto).collect(Collectors.toList());
        return PageResult.of(pageNo, pageSize, total, dtos);
    }

    /**
     * 保存 BizScope 配置（UPSERT：已存在则更新，不存在则新增）
     * 高危操作，写审计日志并发布缓存失效事件
     *
     * @param roleId    角色ID
     * @param bizType   BizType 枚举值字符串
     * @param dataScope DataScopeType 枚举值字符串
     * @param reason    操作原因
     * @return 保存后的 BizScopeRespDTO
     */
    @Transactional
    public BizScopeRespDTO saveBizScope(String roleId, String bizType, String dataScope, String reason) {
        PtRole role = roleMapper.selectByRoleId(roleId);
        if (role == null) {
            throw new BizException(AuthErrorCode.ROLE_NOT_FOUND.getCode(), AuthErrorCode.ROLE_NOT_FOUND.getMessage());
        }
        // 校验枚举合法性
        BizType.valueOf(bizType);
        DataScopeType.valueOf(dataScope);

        PtRoleBizScope existing = roleBizScopeMapper.selectByRoleIdAndBizType(roleId, bizType);
        PtRoleBizScope entity;

        if (existing != null) {
            // 更新已有配置
            existing.setDataScope(dataScope);
            roleBizScopeMapper.updateById(existing);
            entity = existing;
        } else {
            // 新增配置，ID 格式：S_ + ROLE_CODE + _ + BIZ_TYPE
            entity = new PtRoleBizScope();
            entity.setId("S_" + role.getRoleCode() + "_" + bizType);
            entity.setRoleId(roleId);
            entity.setBizType(bizType);
            entity.setDataScope(dataScope);
            entity.setRecordStatus(0);
            roleBizScopeMapper.insert(entity);
        }

        cacheService.evictBizScopeCache(roleId);
        publishCacheInvalidatedEvent("BIZ_SCOPE", Set.of(roleId), null, reason);

        BizScopeRespDTO dto = toDto(entity);
        dto.setRoleChName(role.getRoleChName());
        return dto;
    }

    /**
     * 删除 BizScope 配置
     * 高危操作，写审计日志并发布缓存失效事件
     *
     * @param id     PT_ROLE_BIZ_SCOPE.ID
     * @param reason 操作原因
     */
    @Transactional
    public void deleteBizScope(String id, String reason) {
        PtRoleBizScope scope = roleBizScopeMapper.selectById(id);
        if (scope == null) {
            throw new BizException(AuthErrorCode.BIZ_SCOPE_NOT_FOUND.getCode(),
                AuthErrorCode.BIZ_SCOPE_NOT_FOUND.getMessage());
        }
        roleBizScopeMapper.deleteById(id);
        cacheService.evictBizScopeCache(scope.getRoleId());
        publishCacheInvalidatedEvent("BIZ_SCOPE", Set.of(scope.getRoleId()), null, reason);
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    private BizScopeRespDTO toDto(PtRoleBizScope scope) {
        BizScopeRespDTO dto = new BizScopeRespDTO();
        dto.setId(scope.getId());
        dto.setRoleId(scope.getRoleId());
        dto.setBizType(scope.getBizType());
        dto.setDataScope(scope.getDataScope());
        dto.setRecordStatus(scope.getRecordStatus());
        dto.setCreateTime(scope.getCreateTime());
        dto.setUpdateTime(scope.getUpdateTime());
        return dto;
    }

    private void publishCacheInvalidatedEvent(String changeType, Set<String> roleIds,
                                              Set<String> userIds, String reason) {
        PermissionCacheInvalidatedEvent event = new PermissionCacheInvalidatedEvent();
        event.setChangeType(changeType);
        event.setAffectedRoleIds(roleIds);
        event.setAffectedUserIds(userIds);
        event.setOperator("SYSTEM");
        event.setReason(reason);
        event.setEventId("evt_" + System.currentTimeMillis());
        event.setOccurredAt(System.currentTimeMillis());
        eventPublisher.publishEvent(event);
    }
}
