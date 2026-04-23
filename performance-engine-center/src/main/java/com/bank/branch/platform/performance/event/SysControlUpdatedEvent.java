package com.bank.branch.platform.performance.event;

import lombok.Getter;

/**
 * sys_control 版本变更事件（V1.2 Q1.1）.
 *
 * <p>触发场景：
 * <ul>
 *   <li>{@code SysControlService.doSwitchVersion} 手动切换 (publishSource=MANUAL)</li>
 *   <li>{@code SysControlService.rollback} 版本回滚 (publishSource=ROLLBACK)</li>
 *   <li>自动切换 Job (publishSource=AUTO，V1.2 规划中)</li>
 * </ul>
 *
 * <p>消费方：下游缓存失效、报表快照重建、通知推送。
 *
 * @since V1.2 Q1.1
 */
@Getter
public class SysControlUpdatedEvent extends PerfDomainEvent {

    /** 维度: EMP / ORG / CUST. */
    private final String scopeDim;

    /** 切换前的版本号. */
    private final String oldVersion;

    /** 切换后的版本号. */
    private final String newVersion;

    /** 发布来源: MANUAL / AUTO / ROLLBACK. */
    private final String publishSource;

    /** 发布人 empId. */
    private final String publishBy;

    public SysControlUpdatedEvent(String traceId,
                                  String scopeDim,
                                  String oldVersion,
                                  String newVersion,
                                  String publishSource,
                                  String publishBy) {
        super(traceId);
        this.scopeDim = scopeDim;
        this.oldVersion = oldVersion;
        this.newVersion = newVersion;
        this.publishSource = publishSource;
        this.publishBy = publishBy;
    }

    @Override
    public String topic() {
        return "performance.sys-control.updated.v1";
    }
}
