package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外部数据任务状态上报响应 DTO（V1.1 Task P6.1）.
 *
 * <p>对齐 03 §G.1 示例响应：
 * <pre>
 * {
 *   "taskId": "EXT_20260410_ALLOC_01",
 *   "accepted": true,
 *   "perfRunTaskId": 9102
 * }
 * </pre>
 *
 * <p>幂等场景：重复上报时 {@code accepted=false}，但仍返回既有的 {@code perfRunTaskId}，
 * 调用方可据此判断是否首次处理。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "外部数据任务状态上报响应")
public class DataTaskStatusRespDTO {

    /** 外部任务 ID（回显，便于调用方对账）. */
    @Schema(description = "外部任务 ID（回显）")
    private String taskId;

    /** 是否本次新接受（false 表示幂等命中既有记录）. */
    @Schema(description = "是否本次新接受；false=幂等命中")
    private Boolean accepted;

    /** 内部 PERF_RUN_TASK 主键（EXT_DATA 任务 ID）. */
    @Schema(description = "内部 PERF_RUN_TASK 主键")
    private String perfRunTaskId;
}
