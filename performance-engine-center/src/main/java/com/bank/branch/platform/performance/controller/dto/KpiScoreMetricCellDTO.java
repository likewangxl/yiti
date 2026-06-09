package com.bank.branch.platform.performance.controller.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * KPI 计算结果详情：某对象在某指标上的一格数据.
 * 用于「指标组」第二行：实际值 / 目标值 / 基础值 / 完成率 / 得分.
 */
@Data
public class KpiScoreMetricCellDTO {

    /** 实际值. */
    private BigDecimal actual;

    /** 目标值. */
    private BigDecimal target;

    /** 基础值. */
    private BigDecimal base;

    /** 完成率(%)：(实际值-基础值)/目标值*100；目标值为 0/空时为 null. */
    private BigDecimal completeRate;

    /** 得分（KPI 得分）. */
    private BigDecimal score;
}
