package com.bank.branch.platform.performance.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 指标试运行响应 DTO（03 §A.5）.
 *
 * <p>V1.5 P1.1：兼容过渡期结束，删除 {@code @Deprecated getSamples()} 与
 * {@code @JsonAlias({"samples"})}。客户端必须使用 {@code sampleRows} 字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricTrialRespDTO {

    /** 本次试运行任务 ID. */
    private String taskId;

    /** 回显的指标编码. */
    private String metricCode;

    /** 实际返回样本条数. */
    private Integer sampleSize;

    /** 总行数（EXPR 为 1）. */
    private Integer totalRows;

    /** 执行状态：RUNNING/SUCCESS/FAILED（Service 同步执行固定 SUCCESS）. */
    private String status;

    /** 执行开始时间. */
    private LocalDateTime startedAt;

    /** 执行结束时间. */
    private LocalDateTime endedAt;

    /** 错误信息（失败时填入）. */
    private String errorMsg;

    /** EXPR 单值结果（SQL 场景为 null）. */
    private BigDecimal exprResult;

    /** 执行耗时（毫秒）. */
    private Long executionMillis;

    /** 样本行（SQL 场景填充，EXPR 为空列表）. */
    private List<Map<String, Object>> sampleRows;
}
