package com.bank.branch.platform.governance.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * GOV 模块错误码枚举
 * 格式：GOV-{HTTP状态码}{序号}
 */
@Getter
@AllArgsConstructor
public enum GovErrorCode {

    // 404 资源不存在
    DICT_TYPE_NOT_FOUND("GOV-40001", "字典类型不存在"),
    CONFIG_NOT_FOUND("GOV-40002", "配置项不存在"),
    CALENDAR_NOT_FOUND("GOV-40003", "日历记录不存在"),
    TASK_NOT_FOUND("GOV-40004", "任务不存在"),
    FILE_NOT_FOUND("GOV-40005", "文件不存在"),
    NOTICE_NOT_FOUND("GOV-40006", "通知不存在"),
    TASK_LOG_NOT_FOUND("GOV-40007", "任务执行日志不存在"),

    // 403 禁止操作
    PAST_DATE_NOT_MODIFIABLE("GOV-40301", "过去日期不可修改"),

    // 409 冲突
    DICT_CODE_DUPLICATE("GOV-40901", "字典编码重复"),
    CONFIG_KEY_DUPLICATE("GOV-40902", "配置Key重复"),
    TASK_ALREADY_RUNNING("GOV-40903", "任务正在执行中"),

    // 422 校验失败
    NOT_SELECT_SQL("GOV-42201", "非SELECT SQL语句"),
    SQL_CONCURRENCY_EXCEEDED("GOV-42202", "SQL并发数超限"),
    FILE_FORMAT_INVALID("GOV-42203", "文件格式不合法"),
    FILE_SIZE_EXCEEDED("GOV-42204", "文件大小超限"),
    CONFIG_VALUE_TYPE_INVALID("GOV-42205", "配置值类型校验失败"),

    // 500 内部错误
    MINIO_ERROR("GOV-50001", "MinIO服务异常"),
    REDIS_CACHE_ERROR("GOV-50002", "Redis缓存异常"),
    SQL_EXECUTION_TIMEOUT("GOV-50003", "SQL执行超时");

    private final String code;
    private final String message;
}
