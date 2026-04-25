package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 绩效汇总行 VO（C.3 GET /perf-summary，Task M3.2.1）.
 *
 * <p>来源：performance.KpiApi.getCurrentKpiTotal(empId, cycleType) → BigDecimal 总分.
 *
 * <p>V1.0 简化：单条循环装配，未带 KPI 明细分项；V1.1 切 batchGet 后可再扩展 items 字段.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PerfSummaryRowVO {

    /** 主体 ID（empId 或 orgCode） */
    private String subjectId;

    /** 主体名称（V1.0 直接回填 subjectId） */
    private String subjectName;

    /** KPI 周期类型 */
    private String cycleType;

    /** KPI 总分（V1.1 P4.3 实际交付） */
    private BigDecimal totalScore;
}
