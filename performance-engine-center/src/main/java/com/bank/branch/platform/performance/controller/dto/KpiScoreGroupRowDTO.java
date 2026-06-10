package com.bank.branch.platform.performance.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * KPI 计算结果详情：按对象分组的一行.
 * 固定列：对象ID / 对象姓名 / 考核得分(合计)；动态：每个指标一格 {@link KpiScoreMetricCellDTO}.
 */
@Data
public class KpiScoreGroupRowDTO {

    /** 对象ID（subject_id；ORG 维度展示业务机构号 dept_no）. */
    private String subjectId;

    /** 对象类型（EMP/ORG/CUST）. */
    private String subjectType;

    /** 对象姓名（EMP=员工姓名，ORG=机构名称；解析不到为 null）. */
    private String subjectName;

    /** 考核得分：该对象所有指标得分合计. */
    private BigDecimal totalScore;

    /** 各指标格：metricCode → 该指标的实际/目标/基础/完成率/得分. */
    private Map<String, KpiScoreMetricCellDTO> metrics;
}
