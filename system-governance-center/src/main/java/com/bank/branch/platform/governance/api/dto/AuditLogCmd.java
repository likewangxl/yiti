package com.bank.branch.platform.governance.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 审计日志写入命令
 * 用于 AuditApi.log() 方法的入参
 */
@Data
@Builder
public class AuditLogCmd {

    /** 链路追踪ID（必填） */
    private String traceId;

    /** 操作人工号（必填） */
    private String empId;

    /** 操作人姓名 */
    private String empName;

    /** 业务类型（必填，如 SYS_CONFIG、LEAD、CUSTOMER） */
    private String bizType;

    /** 业务动作（必填，如 EXPORT、IMPORT、DELETE、JOB_TRIGGER） */
    private String bizAction;

    /** 资源URL */
    private String resourceUrl;

    /** 请求方法（GET/POST/PUT/DELETE） */
    private String requestMethod;

    /** 请求参数（脱敏后） */
    private String requestParams;

    /** 响应状态码 */
    private Integer responseStatus;

    /** 错误信息 */
    private String errorMsg;

    /** IP地址 */
    private String ipAddress;

    /** 用户代理 */
    private String userAgent;

    /** 执行耗时(ms) */
    private Integer executionTime;

    /** 操作原因（TRANSFER/DELETE/IMPORT/RECALC/CONFIG/JOB_TRIGGER 时必填） */
    private String reason;
}
