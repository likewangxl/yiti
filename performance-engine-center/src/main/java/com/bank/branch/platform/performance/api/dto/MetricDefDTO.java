package com.bank.branch.platform.performance.api.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 指标定义 DTO (对外 API 使用).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricDefDTO {
    /** 指标编码 (唯一). */
    private String metricCode;
    /** 指标名称. */
    private String metricName;
    /** 英文名 (可选). */
    private String metricNameEn;
    /** 指标说明. */
    private String description;
    /** 基础维度: EMP/ORG/CUST. */
    private String baseDim;
    /** 指标层级: 1/2/3. */
    private Integer metricLevel;
    /** 计算频率: DAY/MONTH/QUARTER/YEAR. */
    private String calcFreq;
    /** 计算方式: AUTO/MANUAL. */
    private String calcMode;
    /** 逻辑类型: SQL/PROC/EXPR/SUMMARY. */
    private String calcLogicType;
    /** 宽表槽位 (1..200). */
    private Integer valSlot;
    /** 状态: DRAFT/PUBLISHED/DISABLED/ACTIVE. */
    private String status;
    /** 引用指标编码列表 (JSON 数组字符串). */
    private String refMetricCodes;
}
