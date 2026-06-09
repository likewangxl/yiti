package com.bank.branch.platform.performance.controller.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * KPI 方案级计算记录列表项 DTO（来源：PERF_KPI_CALC_LOG）.
 *
 * <p>考核计算页面数据列表展示用：数据日期 / KPI方案 / 触发方式 / 执行结果 / 开始时间 / 结束时间。
 */
@Data
public class PerfKpiCalcLogDTO {

    /** 主键. */
    private Long id;

    /** 数据日期. */
    private LocalDate dataDate;

    /** KPI 方案编码. */
    private String schemeCode;

    /** 触发方式 AUTO 自动 / MANUAL 手动. */
    private String triggerType;

    /** 触发人工号（PT_USER.username，自动触发为空）. */
    private String triggerBy;

    /** 触发人中文姓名（按 triggerBy=username 反查 PT_USER.userchnname，自动触发为空）. */
    private String triggerByName;

    /** 执行结果 SUCCESS / FAILED. */
    private String result;

    /** 该方案计算开始时间. */
    private LocalDateTime startTime;

    /** 该方案计算结束时间. */
    private LocalDateTime endTime;

    /** 计分对象数. */
    private Integer scoredCount;

    /** 跳过指标项数. */
    private Integer skippedCount;

    /** 异常信息（失败时填）. */
    private String errorMsg;
}
