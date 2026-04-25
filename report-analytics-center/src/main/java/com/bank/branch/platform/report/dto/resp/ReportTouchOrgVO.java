package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 触达任务机构汇总 VO（C.2 GET /touch-task-summary，Task M3.1.1）.
 *
 * <p>从 customer.TouchTaskSummaryDTO 装配：
 * <ul>
 *   <li>orgCode / orgName：直接透传</li>
 *   <li>totalTask / successCount / failedCount：由上游 totalCount / successCount / cancelledCount 计算</li>
 *   <li>successRate：{@code successCount / totalTask}，保留 4 位小数</li>
 * </ul>
 *
 * <p>备注（V1.0 简化）：customer 上游 DTO 无 "failed" 字段，
 * 本 VO 把 cancelledCount 当作 failedCount 填入（plan 05 签名口径）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReportTouchOrgVO {

    /** 机构 ID */
    private String orgCode;

    /** 机构名称 */
    private String orgName;

    /** 任务总数 */
    private Integer totalTask;

    /** 成功任务数 */
    private Integer successCount;

    /** 失败 / 取消任务数 */
    private Integer failedCount;

    /** 成功率（4 位小数） */
    private BigDecimal successRate;
}
