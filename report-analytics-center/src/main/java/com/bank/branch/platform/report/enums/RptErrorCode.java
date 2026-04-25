package com.bank.branch.platform.report.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * report-analytics-center 模块错误码定义（Task M0.3.1）.
 *
 * <p>格式: RPT-{HTTP_STATUS}{SEQ}
 * <p>权威来源: docs/modules/report-analytics-center/02-后端架构.md §6.5「基线 25 条」
 * <p>使用: throw new RptException(RptErrorCode.SAVED_QUERY_NOT_FOUND, id);
 *
 * <p>V1 基线 25 条（业务 10 + 权限 3 + SQL 探查 9 + 系统 3）：
 * <ul>
 *   <li>400xx 业务错误 10 条（saved-query / data-version / subject / metric / export-task）</li>
 *   <li>403xx 权限 3 条（DASHBOARD / SQL_PROBE / DATA_SCOPE）</li>
 *   <li>420xx SQL 探查 9 条（含 plan F1 漏项 42004 / 42006 / 42009）</li>
 *   <li>500xx 系统 3 条（含 plan F1 漏项 50002 / 50003 EXPORT_START_FAILED）</li>
 * </ul>
 *
 * <p>M5.4 异步导出再扩展 5 条 J 章导出限制码（RPT-42207~42211），最终合计 30 条。
 * 新增时必须同步更新 {@code RptErrorCodeTest}。
 */
@Getter
@AllArgsConstructor
public enum RptErrorCode {

    // =============================================
    // 400xx 业务错误（10 条）
    // =============================================

    /** 查询方案不存在 */
    SAVED_QUERY_NOT_FOUND("RPT-40001", "查询方案不存在"),

    /** 无权访问查询方案 */
    SAVED_QUERY_NO_ACCESS("RPT-40002", "无权访问查询方案"),

    /** 查询方案保存上限（最多 10 个） */
    SAVED_QUERY_LIMIT_EXCEEDED("RPT-40003", "最多保存 10 个查询方案"),

    /** 数据日期对应的数据版本不可用 */
    DATA_VERSION_UNAVAILABLE("RPT-40004", "数据日期对应的数据版本不可用"),

    /** 查询对象不在数据范围内 */
    SUBJECT_OUT_OF_SCOPE("RPT-40005", "查询对象不在数据范围内"),

    /** 指标不属于该维度 */
    METRIC_DIM_MISMATCH("RPT-40006", "指标不属于该维度"),

    /** 查询对象数超出限制（最多 100 个） */
    SUBJECT_SIZE_EXCEEDED("RPT-40007", "查询对象数超出限制（最多 100 个）"),

    /** 查询指标数超出限制（最多 20 个） */
    METRIC_SIZE_EXCEEDED("RPT-40008", "查询指标数超出限制（最多 20 个）"),

    /** 导出任务不存在 */
    EXPORT_TASK_NOT_FOUND("RPT-40009", "导出任务不存在"),

    /** 导出任务尚未完成 */
    EXPORT_TASK_NOT_READY("RPT-40010", "导出任务尚未完成"),

    // =============================================
    // 403xx 权限（3 条）
    // =============================================

    /** 无权访问仪表盘 */
    DASHBOARD_NO_ACCESS("RPT-40301", "无权访问仪表盘"),

    /** 无权使用 SQL 探查 */
    SQL_PROBE_NO_ACCESS("RPT-40302", "无权使用 SQL 探查"),

    /** 数据范围不足 */
    DATA_SCOPE_INSUFFICIENT("RPT-40303", "数据范围不足"),

    // =============================================
    // 420xx SQL 探查（9 条）
    // =============================================

    /** SQL 语法校验失败 */
    SQL_PARSE_FAILED("RPT-42001", "SQL 语法校验失败"),

    /** SQL 访问了白名单外的表 */
    SQL_TABLE_NOT_WHITELISTED("RPT-42002", "SQL 访问了白名单外的表"),

    /** SQL 包含禁用关键字 */
    SQL_FORBIDDEN_KEYWORD("RPT-42003", "SQL 包含禁用关键字"),

    /** SQL 结果超出行数限制 */
    SQL_ROW_LIMIT_EXCEEDED("RPT-42004", "SQL 结果超出行数限制"),

    /** SQL 执行超时 */
    SQL_EXECUTION_TIMEOUT("RPT-42005", "SQL 执行超时"),

    /** SQL 并发数超限 */
    SQL_CONCURRENT_LIMIT("RPT-42006", "SQL 并发数超限"),

    /** SQL 仅允许 SELECT 语句 */
    SQL_ONLY_SELECT_ALLOWED("RPT-42007", "SQL 仅允许 SELECT 语句"),

    /** SQL 长度超出限制 */
    SQL_LENGTH_EXCEEDED("RPT-42008", "SQL 长度超出限制"),

    /** SQL 执行失败 */
    SQL_EXECUTION_FAILED("RPT-42009", "SQL 执行失败"),

    // =============================================
    // 500xx 系统错误（3 条）
    // =============================================

    /** 跨模块调用失败 */
    CROSS_MODULE_CALL_FAILED("RPT-50001", "跨模块调用失败"),

    /** 缓存读取失败 */
    CACHE_READ_FAILED("RPT-50002", "缓存读取失败"),

    /** 异步导出任务启动失败 */
    EXPORT_START_FAILED("RPT-50003", "异步导出任务启动失败");

    private final String code;
    private final String msg;
}
