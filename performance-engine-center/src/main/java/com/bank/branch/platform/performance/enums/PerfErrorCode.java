package com.bank.branch.platform.performance.enums;

/**
 * performance-engine-center 模块错误码定义.
 *
 * <p>格式: PERF-{HTTP_STATUS_LAST_TWO}{SEQ_3DIGIT}
 * <p>使用: throw new PerfException(PerfErrorCode.METRIC_NOT_FOUND, metricCode);
 */
public enum PerfErrorCode {

    /* 400 参数校验 */
    PARAM_INVALID("PERF-40001", "请求参数非法: %s"),
    PAGE_OUT_OF_RANGE("PERF-40002", "分页参数越界"),

    /* 404 资源不存在 */
    METRIC_NOT_FOUND("PERF-40401", "指标不存在: %s"),
    KPI_SCHEME_NOT_FOUND("PERF-40402", "KPI方案不存在: %s"),
    TARGET_PLAN_NOT_FOUND("PERF-40403", "目标方案不存在: %s"),
    TARGET_VALUE_NOT_FOUND("PERF-40404", "目标值不存在: %s"),
    RUN_TASK_NOT_FOUND("PERF-40405", "运行任务不存在: %s"),
    SYS_CONTROL_NOT_FOUND("PERF-40406", "版本控制记录不存在: scopeDim=%s"),
    KPI_ITEM_NOT_FOUND("PERF-40407", "KPI指标项不存在: %s"),

    /* 409 冲突 */
    METRIC_SLOT_CONFLICT("PERF-40901", "槽位已被占用: baseDim=%s, slot=%d"),
    METRIC_CYCLE_DETECTED("PERF-40902", "指标引用形成环路: %s"),
    METRIC_CODE_DUP("PERF-40903", "指标编码已存在: %s"),
    SYS_CONTROL_CONFLICT("PERF-40904", "版本切换并发冲突"),
    INVALID_STATE("PERF-40905", "当前状态不允许此操作: %s"),
    PLAN_NOT_PUBLISHED("PERF-40906", "方案未发布不可绑定目标: %s"),
    KPI_SCHEME_CODE_DUP("PERF-40907", "KPI方案编码已存在: %s"),
    TARGET_PLAN_CODE_DUP("PERF-40908", "目标方案编码已存在: %s"),
    KPI_ITEM_DUP("PERF-40909", "KPI方案内指标项已存在: schemeId=%s, metricCode=%s"),
    TARGET_BATCH_TOO_BIG("PERF-40910", "批量目标值最多500条, 当前: %d"),
    METRIC_LEVEL_INVALID("PERF-40911", "指标层级引用违规: %s"),
    METRIC_STILL_REFERENCED("PERF-40912", "指标仍被其他指标引用, 不可删除: %s"),
    SLOT_LOCK_ACQUIRE_FAILED("PERF-40913", "槽位分配锁获取失败, 请重试: baseDim=%s"),
    KPI_PUBLISH_METRIC_INVALID("PERF-40914", "KPI方案发布时引用的指标不可用 (不存在或已停用): %s"),
    TARGET_PLAN_KPI_SCHEME_INVALID("PERF-40915", "目标方案引用的 KPI 方案不可用 (未发布或已停用): %s"),

    /* 500 服务端错误 */
    INTERNAL_ERROR("PERF-50001", "未预期的服务端错误"),
    DOWNSTREAM_ERROR("PERF-50002", "下游依赖异常: %s");

    private final String code;
    private final String messageTemplate;

    PerfErrorCode(String code, String messageTemplate) {
        this.code = code;
        this.messageTemplate = messageTemplate;
    }

    public String getCode() {
        return code;
    }

    public String getMessageTemplate() {
        return messageTemplate;
    }

    /** 填充消息模板占位符, 返回最终异常消息. */
    public String format(Object... args) {
        if (args == null || args.length == 0) {
            return messageTemplate;
        }
        return String.format(messageTemplate, args);
    }
}
