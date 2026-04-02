package com.bank.branch.platform.auth.api.event;

import lombok.Data;

import java.util.Set;

/**
 * 权限缓存失效事件
 * 当角色、资源、业务范围或用户角色绑定发生变更时发布此事件
 * 监听方负责清除本地/Redis相关缓存，确保权限实时生效
 */
@Data
public class PermissionCacheInvalidatedEvent {

    /** 变更类型：ROLE / RESOURCE / BIZ_SCOPE / USER_ROLE / ROLE_RESOURCE */
    private String changeType;

    /** 受影响的角色ID集合 */
    private Set<String> affectedRoleIds;

    /** 受影响的员工ID集合 */
    private Set<String> affectedUserIds;

    /** 操作人员工ID */
    private String operator;

    /** 变更原因说明 */
    private String reason;

    /** 事件唯一标识（用于幂等处理） */
    private String eventId;

    /** 事件发生时间戳（毫秒） */
    private Long timestamp;
}
