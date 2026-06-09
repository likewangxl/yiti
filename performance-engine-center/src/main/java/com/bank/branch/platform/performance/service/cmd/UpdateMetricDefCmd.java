package com.bank.branch.platform.performance.service.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新指标定义命令.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMetricDefCmd {

    /** 指标编码（作为更新键）. */
    private String metricCode;

    /** 指标名称. */
    private String metricName;

    /** 指标英文名. */
    private String metricNameEn;

    /** 指标说明. */
    private String metricDesc;

    /** 指标详细描述（前端"指标详细描述"输入框，原样保存）. */
    private String description;

    /** 计算频率. */
    private String calcFreq;

    /** 计算方式. */
    private String calcMode;

    /** 逻辑类型. */
    private String calcLogicType;

    /** SQL 文本. */
    private String sqlText;

    /** 表达式文本（指标编号 Groovy）. */
    private String exprText;

    /** 汇总规则. */
    private String summaryRule;

    /** 引用指标 JSON 字符串. */
    private String refMetricCodes;

    /** V1.9 指标分类（规模类/效益类/质量类/合规类等）—— 补齐 CreateMetricDefCmd 同字段. */
    private String metricCategory;

    /** 更新人. */
    private String operator;
}
