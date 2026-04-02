package com.bank.branch.platform.auth.api.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.context.ApplicationEvent;

import java.util.Set;

/**
 * 权限缓存失效事件
 * 当角色、资源、业务范围或用户角色绑定发生变更时发布此事件
 * 监听方负责清除本地/Redis相关缓存，确保权限实时生效
 * <p>
 * 继承 ApplicationEvent 以便通过 ApplicationEventPublisher.publishEvent(ApplicationEvent) 发布，
 * 使接收方可通过标准 @EventListener 注解或 ApplicationListener 接口监听。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class PermissionCacheInvalidatedEvent extends ApplicationEvent {

    /**
     * 无参构造，使用 "PERMISSION_SYSTEM" 作为事件来源占位符。
     * Spring 事件机制要求 source 非 null，此处以系统标识代替具体对象。
     */
    public PermissionCacheInvalidatedEvent() {
        super("PERMISSION_SYSTEM");
    }

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

    /** 事件发生时间戳（毫秒），字段名为 occurredAt 避免与 ApplicationEvent.getTimestamp() 冲突 */
    private Long occurredAt;
}
