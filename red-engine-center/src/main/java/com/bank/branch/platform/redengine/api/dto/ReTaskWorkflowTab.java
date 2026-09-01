package com.bank.branch.platform.redengine.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 任务工作台页签。
 *
 * <p>页签由服务端归一为状态集合，避免客户端只拿到一页数据后再做本地过滤，
 * 造成 total 与 records 不一致。</p>
 */
public enum ReTaskWorkflowTab {

    /** 当前用户需要处理或重新填报的任务。 */
    PENDING,
    /** 已提交并处于审核链路中的任务。 */
    REVIEWING,
    /** 已完成审核的任务。 */
    PASSED,
    /** 任一级审核驳回的任务。 */
    REJECTED;

    /** 兼容前端 lower-case 与中文页签值。 */
    @JsonCreator
    public static ReTaskWorkflowTab fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toUpperCase()) {
            case "PENDING", "待处理" -> PENDING;
            case "REVIEWING", "审核中" -> REVIEWING;
            case "PASSED", "已通过" -> PASSED;
            case "REJECTED", "已驳回" -> REJECTED;
            default -> throw new IllegalArgumentException("不支持的任务工作台页签: " + value);
        };
    }
}
