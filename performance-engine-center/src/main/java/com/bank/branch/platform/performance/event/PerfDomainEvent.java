package com.bank.branch.platform.performance.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * performance-engine-center 领域事件抽象基类。
 *
 * <p>所有 performance 模块内的领域事件必须通过 {@link PerfEventPublisher} 发布，
 * 以保证在 Spring 事务活跃时延迟到 AFTER_COMMIT 阶段投递（事务提交后才送达）。
 *
 * <p>事件命名约定：{@code performance.<topic>.<verb>.v<version>}，
 * 具体事件类必须实现 {@link #topic()} 返回上述格式的字符串。
 *
 * @since V1.2 Q1.1
 */
public abstract class PerfDomainEvent {

    /** 事件唯一 ID（UUID 去横线，32 字符）. */
    private final String eventId;

    /** 链路 traceId（从 MDC 读取；为 null 时由订阅者自行补全）. */
    private final String traceId;

    /** 事件产生时间. */
    private final LocalDateTime occurredAt;

    /**
     * 构造事件，自动分配 eventId 与 occurredAt.
     *
     * @param traceId 当前链路 traceId，可为 null
     */
    protected PerfDomainEvent(String traceId) {
        this.eventId = UUID.randomUUID().toString().replace("-", "");
        this.traceId = traceId;
        this.occurredAt = LocalDateTime.now();
    }

    /**
     * 事件 topic，格式 {@code performance.<topic>.<verb>.v<version>}.
     *
     * @return topic 字符串
     */
    public abstract String topic();

    public String getEventId() {
        return eventId;
    }

    public String getTraceId() {
        return traceId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
