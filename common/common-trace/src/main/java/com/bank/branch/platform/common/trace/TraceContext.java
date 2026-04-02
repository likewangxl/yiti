package com.bank.branch.platform.common.trace;

import java.time.Instant;

/**
 * 链路上下文模型
 */
public record TraceContext(String traceId, String source, Instant startTime) {}
