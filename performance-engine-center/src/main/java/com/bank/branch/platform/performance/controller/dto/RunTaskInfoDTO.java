package com.bank.branch.platform.performance.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 指标立即执行响应 DTO（03 §A.6 / 04 §5）.
 *
 * <p>V1.3 R3.3：回归 03 §A.6 契约，替代 V1.1 P3 临时使用的 {@code Map<String, Object>}.
 *
 * <p>端点：{@code POST /api/perf/metrics/{metricCode}/execute}
 *
 * <p>字段：
 * <ul>
 *   <li>{@code taskId} —— 根任务 ID（{@code perf_run_task.id}，cascade=true 时为父任务 ID）</li>
 *   <li>{@code status} —— 任务状态（{@code RUNNING/SUCCESS/FAILED}，从 {@code perf_run_task.status} 读取）</li>
 *   <li>{@code metricCode} —— 指标编码（回显）</li>
 *   <li>{@code dataDate} —— 数据日期（回显）</li>
 *   <li>{@code version} —— 数据版本（从 sys_control 当前生效版本解析，或兜底 "v_default"）</li>
 * </ul>
 *
 * <p>与 V1.1 Map 版本相比字段名一致，JSON 结构不变；破坏性影响仅限 Java 反射类型（Controller 签名）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunTaskInfoDTO {

    /** 根任务 ID（cascade=true 时为父任务 ID）. */
    private String taskId;

    /** 任务状态：RUNNING / SUCCESS / FAILED. */
    private String status;

    /** 指标编码（回显）. */
    private String metricCode;

    /** 数据日期（回显）. */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataDate;

    /** 数据版本（从 sys_control 解析或兜底 v_default）. */
    private String version;
}
