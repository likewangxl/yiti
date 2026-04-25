package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 员工仪表盘响应 DTO（C.3 GET /dashboard/emp/{empId}，Task M2.3.2）.
 *
 * <p>V1.0 简化字段集（按 SELF 维度展示个人 KPI）：
 * <ul>
 *   <li>empId</li>
 *   <li>dataDate</li>
 *   <li>summaryMetrics：员工维度 4 项核心指标</li>
 *   <li>kpiTotalScore：KpiApi.getCurrentKpiTotal 当前 KPI 总分</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmpDashboardRespDTO {

    /** 员工 ID（empId） */
    private String empId;

    /** 数据日期 */
    private LocalDate dataDate;

    /** 汇总指标（员工维度 CORE_METRICS） */
    private Map<String, BigDecimal> summaryMetrics;

    /** 当前 KPI 总分（KpiApi.getCurrentKpiTotal） */
    private BigDecimal kpiTotalScore;
}
