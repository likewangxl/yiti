package com.bank.branch.platform.performance.controller.dto;

import lombok.Data;

/**
 * 指标定义 Controller 层响应 DTO.
 *
 * <p>隐藏 entity 内部字段（deleted / createdTime / updatedTime / createdBy / updatedBy），
 * 只暴露业务必需字段，防止 entity 泄漏到 API 层。
 *
 * <p>字段对应关系：
 * <ul>
 *   <li>metricCode  — perf_metric_def.metric_code</li>
 *   <li>metricName  — perf_metric_def.metric_name</li>
 *   <li>metricNameEn — perf_metric_def.metric_name_en</li>
 *   <li>metricDesc  — perf_metric_def.metric_desc</li>
 *   <li>baseDim     — perf_metric_def.base_dim</li>
 *   <li>metricLevel — perf_metric_def.metric_level</li>
 *   <li>calcFreq    — perf_metric_def.calc_freq</li>
 *   <li>calcMode    — perf_metric_def.calc_mode</li>
 *   <li>calcLogicType — perf_metric_def.calc_logic_type</li>
 *   <li>sqlText     — perf_metric_def.sql_text</li>
 *   <li>exprText    — perf_metric_def.expr_text</li>
 *   <li>summaryRule — perf_metric_def.summary_rule</li>
 *   <li>refMetricCodes — perf_metric_def.ref_metric_codes</li>
 *   <li>valSlot     — perf_metric_def.val_slot</li>
 *   <li>status      — perf_metric_def.status</li>
 *   <li>unit        — perf_metric_def.unit</li>
 *   <li>decimalPlaces — perf_metric_def.decimal_places</li>
 *   <li>description — perf_metric_def.description</li>
 * </ul>
 */
@Data
public class MetricDefRespDTO {

    /** 指标ID（varchar(32) 主键）. */
    private String id;

    /** 指标编码（唯一）. */
    private String metricCode;

    /** 指标中文名称. */
    private String metricName;

    /** 指标英文名称. */
    private String metricNameEn;

    /** 指标口径说明. */
    private String metricDesc;

    /** 基础维度：EMP / ORG / CUST. */
    private String baseDim;

    /** 指标层级：1 / 2 / 3. */
    private Integer metricLevel;

    /** 计算频率：DAY / MONTH / QUARTER / YEAR. */
    private String calcFreq;

    /** 计算方式：AUTO / MANUAL. */
    private String calcMode;

    /** 计算逻辑类型：SQL / PROC / EXPR / SUMMARY. */
    private String calcLogicType;

    /** 一级指标 SQL / 存储过程文本. */
    private String sqlText;

    /** 二/三级指标表达式. */
    private String exprText;

    /** 机构汇总规则：SUM / AVG / MAX / MIN / COUNT. */
    private String summaryRule;

    /** 引用指标列表 JSON 数组字符串. */
    private String refMetricCodes;

    /** 宽表槽位（1..200，null 表示未分配）. */
    private Integer valSlot;

    /** 状态：ACTIVE / DISABLED. */
    private String status;

    /** 单位：元/万元/%. */
    private String unit;

    /** 小数位数，默认 2. */
    private Integer decimalPlaces;

    /** 指标详细描述（区别于 metricDesc）. */
    private String description;
}
