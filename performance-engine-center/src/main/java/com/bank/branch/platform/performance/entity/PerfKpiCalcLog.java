package com.bank.branch.platform.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * KPI 方案级计算记录表 PERF_KPI_CALC_LOG 贫血实体.
 *
 * <p>KPI 分值计算任务下，每处理完 / 异常结束一个 KPI 方案落一条记录，
 * 便于按方案查看计算结果与异常（整任务流水仍在 {@code PERF_METRIC_CALC_TASK}）。
 */
@Data
@TableName("PERF_KPI_CALC_LOG")
public class PerfKpiCalcLog {

    /** 主键（bigint AUTO_INCREMENT）. */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 数据日期. */
    private LocalDate dataDate;

    /** KPI 方案编码. */
    private String schemeCode;

    /** 触发方式：AUTO 自动 / MANUAL 手动. */
    private String triggerType;

    /** 触发人工号（PT_USER.username）；自动触发为空. */
    private String triggerBy;

    /** 该方案计算开始时间. */
    private LocalDateTime startTime;

    /** 该方案计算结束时间. */
    private LocalDateTime endTime;

    /** 计算结果：SUCCESS / FAILED. */
    private String result;

    /** 计分对象数. */
    private Integer scoredCount;

    /** 跳过指标项数. */
    private Integer skippedCount;

    /** 异常信息（失败时填）. */
    private String errorMsg;

    /** 关联整任务 PERF_METRIC_CALC_TASK.id. */
    private String taskId;

    /** 创建时间（DB 默认值填充）. */
    private LocalDateTime createdTime;
}
