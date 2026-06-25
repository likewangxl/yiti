package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SQL 探查历史响应（D.2 列表 / D.3 详情，Task M4.3.x）.
 *
 * <p>{@code sqlText} 字段在列表场景按前 200 字截断（避免列表 payload 过大），
 * 详情场景返回完整 SQL；由 Service 层按场景填充。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SqlProbeHistoryRespDTO {

    /** 历史 ID（UUID） */
    private String id;

    /** 执行人工号 */
    private String empId;

    /** 执行人用户名称（由 empId 解析，列表/详情展示用） */
    private String empName;

    /** SQL 语句（列表场景截断 200 字） */
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
