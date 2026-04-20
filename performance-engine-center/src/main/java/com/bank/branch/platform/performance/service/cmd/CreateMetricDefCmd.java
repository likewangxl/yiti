package com.bank.branch.platform.performance.service.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新建指标定义命令.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMetricDefCmd {

    /** 指标编码. */
    private String metricCode;

    /** 指标名称. */
    private String metricName;

    /** 指标英文名. */
    private String metricNameEn;

    /** 指标说明. */
    private String metricDesc;

    /** 基础维度. */
    private String baseDim;

    /** 指标层级. */
    private Integer metricLevel;

    /** 计算频率. */
    private String calcFreq;

    /** 计算方式. */
    private String calcMode;

    /** 逻辑类型. */
    private String calcLogicType;

    /** SQL 文本. */
    private String sqlText;

    /** 表达式文本. */
    private String exprText;

    /** 汇总规则. */
    private String summaryRule;

    /** 引用指标 JSON 字符串. */
    private String refMetricCodes;

    /** 指定槽位. */
    private Integer preferredSlot;

    /** 操作人. */
    private String operator;
}
