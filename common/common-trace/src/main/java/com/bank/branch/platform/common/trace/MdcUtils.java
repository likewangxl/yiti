package com.bank.branch.platform.common.trace;

import org.slf4j.MDC;

/**
 * MDC 工具类
 * 封装 SLF4J MDC 操作，方便在日志中输出 traceId
 */
public class MdcUtils {

    public static final String TRACE_ID_KEY = "traceId";

    private MdcUtils() {}

    public static void putTraceId(String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
    }

    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    public static void removeTraceId() {
        MDC.remove(TRACE_ID_KEY);
    }
}
