package com.bank.branch.platform.performance.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * KPI 计算结果明细列表项 DTO（来源：PERF_KPI_SCORE）.
 *
 * <p>KPI 计算结果详情页展示用：维度（subject_type）/ 指标（metric_code）/ 对象（subject_id）/ 得分（score）。
 */
@Data
public class PerfKpiScoreResultDTO {

    /** 主键. */
    private Long id;

    /** 数据日期. */
    private LocalDate dataDate;

    /** KPI 方案编码. */
    private String schemeCode;

    /** 维度（对象类型 EMP/ORG/CUST）. */
    private String subjectType;

    /** 指标编码. */
    private String metricCode;

    /** 指标名称（由 metricCode 解析）. */
    private String metricName;

    /** 对象（emp_id/org_code/cust_id）. */
    private String subjectId;

    /** 对象名称（EMP→员工姓名 / ORG→机构名称；CUST 暂为空）. */
    private String subjectName;

    /** 实际值. */
    private BigDecimal actualValue;

    /** 权重. */
    private BigDecimal weight;

    /** 目标值. */
    private BigDecimal targetValue;

    /** 基础值. */
    private BigDecimal baseValue;

    /** 得分. */
    private BigDecimal score;
}
