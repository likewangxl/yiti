package com.bank.branch.platform.customer.api.dto;

import lombok.Data;

/**
 * 触达任务汇总统计 DTO
 * <p>
 * 用于跨模块传递按机构维度汇总的触达任务统计数据，包含各状态数量及 SLA 预警数、平均时长等。
 * 主要供 report-analytics-center 聚合展示使用，不携带明细记录。
 * </p>
 */
@Data
public class TouchTaskSummaryDTO {

    /** 机构 ID */
    private String orgId;

    /** 机构名称 */
    private String orgName;

    /** 任务总数 */
    private Long totalCount;

    /** 待处理任务数 */
    private Long pendingCount;

    /** 进行中任务数 */
    private Long inProgressCount;

    /** 已完成任务数 */
    private Long successCount;

    /** 已取消任务数 */
    private Long cancelledCount;

    /** SLA 预警任务数 */
    private Long slaWarningCount;

    /** 平均完成时长 (小时) */
    private Double avgDurationHours;
}
