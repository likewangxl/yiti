package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 历史回算响应 DTO（V1.1 Task P7.2，端点 POST /api/perf/recalc）.
 *
 * <p>对齐 03 §F.2 {@code R<RunTaskInfoDTO>} 语义：返回父级 run_task ID + 初始状态.
 *
 * <p>调用方拿到 {@code taskId} 后可通过 {@code GET /api/perf/run-tasks/{id}}
 * 轮询父 task 状态，并通过 {@code params_json} 的 {@code metricCount / dateCount}
 * 预估总子任务量，通过父 task 的 {@code error_msg}（本实现复用为 remark 聚合）
 * 获取成功/失败明细.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "历史回算响应（父级 run_task 信息）")
public class RecalcRespDTO {

    /** 父级 run_task 主键（taskType=RECALC）. */
    @Schema(description = "父级 run_task 主键")
    private String taskId;

    /** 初始状态（接口返回时一般为 RUNNING 或已完成时为 SUCCESS/PARTIAL/FAILED）. */
    @Schema(description = "父 task 当前状态")
    private String status;
}
