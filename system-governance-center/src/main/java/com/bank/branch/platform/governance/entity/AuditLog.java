package com.bank.branch.platform.governance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志实体，对应 audit_log 表。
 * <p>
 * 审计日志为不可变记录，只有 created_time，没有 updated_time。
 * 一旦写入不可修改、不可删除。
 * </p>
 */
@Data
@TableName("AUDIT_LOG")
public class AuditLog {

    /** 日志ID（UUID主键），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 链路追踪ID，对应 trace_id */
    private String traceId;

    /** 操作人工号，对应 emp_id */
    private String empId;

    /** 操作人姓名，对应 emp_name */
    private String empName;

    /** 业务类型，对应 biz_type */
    private String bizType;

    /** 业务动作，对应 biz_action */
    private String bizAction;

    /** 资源URL，对应 resource_url */
    private String resourceUrl;

    /** 请求方法（GET/POST/PUT/DELETE），对应 request_method */
    private String requestMethod;

    /** 请求参数（脱敏后），对应 request_params（TEXT类型） */
    private String requestParams;

    /** 响应状态码，对应 response_status */
    private Integer responseStatus;

    /** 错误信息，对应 error_msg（TEXT类型） */
    private String errorMsg;

    /** IP地址，对应 ip_address */
    private String ipAddress;

    /** 用户代理，对应 user_agent */
    private String userAgent;

    /** 执行耗时（毫秒），对应 execution_time */
    private Integer executionTime;

    /** 操作原因（高危动作必填），对应 reason */
    private String reason;

    /** 创建时间，对应 created_time（审计日志不可变，无 updated_time） */
    private LocalDateTime createdTime;
}
