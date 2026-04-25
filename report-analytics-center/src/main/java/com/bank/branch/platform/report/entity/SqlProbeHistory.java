package com.bank.branch.platform.report.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * sql_probe_history 实体 —— SQL 探查历史.
 *
 * <p>贫血模型，字段完全对齐 {@code V1_0_0__rpt_init.sql} §2.
 *
 * <p>业务约束：
 * <ul>
 *   <li>每次执行前 INSERT status=RUNNING 占位拿 id，完成时 UPDATE 终态</li>
 *   <li>保留 3 个月（约 90 天），由 sys_job_conf 定时清理</li>
 *   <li>配合 audit_log 做安全审计追溯</li>
 * </ul>
 */
@Data
public class SqlProbeHistory {

    /** 历史 ID（UUID） */
    private String id;

    /** 执行人工号 */
    private String empId;

    /** SQL 语句 */
    private String sqlText;

    /** 备注（reason） */
    private String remark;

    /** 影响行数 */
    private Integer rowCount;

    /** 执行耗时（毫秒） */
    private Integer executionTimeMs;

    /** 状态：RUNNING / SUCCESS / FAILED / TIMEOUT */
    private String status;

    /** 错误信息 */
    private String errorMsg;

    /** 创建时间 */
    private LocalDateTime createdTime;
}
