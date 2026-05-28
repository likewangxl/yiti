package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评价任务概要 DTO（对外 API 使用）.
 * <p>供 {@code report-analytics-center} 等下游模块通过 {@code EvalQueryApi} 只读查询使用。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalTaskSummaryDto {
    /** 任务ID. */
    private Long taskId;
    /** 任务名称. */
    private String taskName;
    /** 任务状态（0=进行中，1=已结束）. */
    private Integer status;
    /** 截止时间. */
    private LocalDateTime endTime;
    /** 被评价人数量. */
    private int targetCount;
}
