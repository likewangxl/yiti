package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 审计日志传输对象
 */
@Data
public class AuditLogDTO {

    /** 日志ID */
    private String id;

    /** 链路追踪ID */
    private String traceId;

    /** 操作人工号 */
    private String empId;

    /** 操作人姓名 */
    private String empName;

    /** 业务类型 */
    private String bizType;

    /** 业务动作 */
    private String bizAction;

    /** 资源URL */
    private String resourceUrl;

    /** 请求方法 */
    private String requestMethod;

    /** 请求参数（脱敏后） */
    private String requestParams;

    /** 响应状态码 */
    private Integer responseStatus;

    /** 错误信息 */
    private String errorMsg;

    /** IP地址 */
    private String ipAddress;

    /** 执行耗时(ms) */
    private Integer executionTime;

    /** 操作原因（高危动作必填） */
    private String reason;

    /** 操作时间 */
    private String createdTime;
}
