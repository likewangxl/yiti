package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外部数据任务状态上报结果 DTO（V1.1 Task P6.1）.
 *
 * <p>跨模块对外 Api 返回结果：封装幂等判断（accepted）和内部 perf_run_task 主键，
 * 供 REST 层（DataTaskController）装配响应，或其他模块内部集成时使用。
 *
 * <p>语义：
 * <ul>
 *   <li>{@code accepted=true}：本次上报首次落库，已触发后续计算管线</li>
 *   <li>{@code accepted=false}：幂等命中既有 taskId，返回既有 perfRunTaskId，
 *       <em>不</em>重复触发计算</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataTaskReportResultDTO {

    /** 外部任务 ID（回显，便于调用方对账）. */
    private String taskId;

    /** 是否本次新接受（false=幂等命中）. */
    private Boolean accepted;

    /** 内部 perf_run_task 主键（EXT_DATA 任务 ID）. */
    private String perfRunTaskId;
}
