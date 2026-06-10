package com.bank.branch.platform.performance.mapper;

import lombok.Data;

import java.math.BigDecimal;

/**
 * PERF_KPI_SCORE 按对象分组投影：data_date + scheme_code 下，按 (subject_id, subject_type)
 * group by 后的一行——对象ID / 对象类型 / 考核得分(该对象所有指标 score 合计).
 */
@Data
public class KpiSubjectGroupRow {

    /** 对象ID（subject_id）. */
    private String subjectId;

    /** 对象类型（subject_type：EMP/ORG/CUST）. */
    private String subjectType;

    /** 考核得分：该对象所有指标 score 合计（SUM(score)）. */
    private BigDecimal totalScore;
}
