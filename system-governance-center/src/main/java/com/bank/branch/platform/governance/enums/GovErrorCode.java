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
    PERSON_TAG_NOT_FOUND("GOV-40008", "人员标签不存在"),
    PERSON_TAG_MEMBER_NOT_FOUND("GOV-40009", "标签成员不存在"),

    // 403 禁止操作
    PAST_DATE_NOT_MODIFIABLE("GOV-40301", "过去日期不可修改"),
    JOB_MANUAL_NOT_ALLOWED("GOV-40302", "该任务不允许手动触发"),
    JOB_NOT_ALLOWED("GOV-40303", "任务未列入当前 Scheduler 允许集合"),

    // 409 冲突
    DICT_CODE_DUPLICATE("GOV-40901", "字典编码重复"),
    CONFIG_KEY_DUPLICATE("GOV-40902", "配置Key重复"),
    TASK_ALREADY_RUNNING("GOV-40903", "任务正在执行中"),
    PERSON_TAG_NAME_DUPLICATE("GOV-40904", "标签名称已存在"),
    PERSON_TAG_MEMBER_DUPLICATE("GOV-40905", "该员工已在标签下"),

    // 422 校验失败
    NOT_SELECT_SQL("GOV-42201", "非SELECT SQL语句"),
    SQL_CONCURRENCY_EXCEEDED("GOV-42202", "SQL并发数超限"),
    FILE_FORMAT_INVALID("GOV-42203", "文件格式不合法"),
    FILE_SIZE_EXCEEDED("GOV-42204", "文件大小超限"),
    CONFIG_VALUE_TYPE_INVALID("GOV-42205", "配置值类型校验失败"),
    PERSON_TAG_IMPORT_FILE_EMPTY("GOV-42206", "导入文件为空"),
    PERSON_TAG_IMPORT_FILE_INVALID("GOV-42207", "导入文件解析失败"),
    PERSON_TAG_USERNAME_NOT_EXISTS("GOV-42208", "员工工号在系统中不存在"),
    PERSON_TAG_DEPTNO_NOT_EXISTS("GOV-42209", "机构编号在系统中不存在"),
    PERSON_TAG_MEMBER_EMPTY("GOV-42210", "请至少填写一个员工工号或机构编号"),

    // 500 内部错误
    MINIO_ERROR("GOV-50001", "MinIO服务异常"),
    REDIS_CACHE_ERROR("GOV-50002", "Redis缓存异常"),
    SQL_EXECUTION_TIMEOUT("GOV-50003", "SQL执行超时"),
    JOB_TRIGGER_FAILED("GOV-50004", "任务触发失败"),
    JOB_PAUSE_FAILED("GOV-50005", "Job 暂停失败"),
    JOB_RESUME_FAILED("GOV-50006", "Job 恢复失败"),

    /** OBS 外联被运行环境显式关闭。 */
    OBS_DISABLED("GOV-50301", "OBS 对象存储已禁用"),

    /** Cron 表达式非法 (V1.7). */
    JOB_CRON_INVALID("GOV-50010", "cron 表达式非法"),

    /** quartz_job_class 反射加载失败 (V1.7). */
    JOB_CLASS_NOT_FOUND("GOV-50011", "quartz_job_class 反射失败"),

    /** Quartz Scheduler 注册失败 (V1.7). */
    JOB_REGISTER_FAILED("GOV-50012", "Quartz Scheduler 注册失败");

    private final String code;
    private final String message;
}
