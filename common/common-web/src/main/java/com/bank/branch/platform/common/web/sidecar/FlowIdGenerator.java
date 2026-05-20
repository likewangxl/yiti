package com.bank.branch.platform.common.web.sidecar;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 边车流水号生成器。
 * <p>格式：前缀(G/C) + yyyyMMddHHmmssSSS + UUID 后 12 位（大写）。
 * <ul>
 *   <li>global_flow_no：全局流水号，G 开头</li>
 *   <li>cons_flow_no：消费方流水号，C 开头</li>
 * </ul>
 * 既可读（前缀+时间戳）又唯一（UUID 尾），便于日志追踪。
 */
public class FlowIdGenerator {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /** 全局流水号 */
    public String genGlobalFlowNo() {
        return "G" + LocalDateTime.now().format(TS) + tail();
    }

    /** 消费方流水号 */
    public String genConsFlowNo() {
        return "C" + LocalDateTime.now().format(TS) + tail();
    }

    private String tail() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
