package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * KPI 结果 DTO.
 * <p>V1.0 仅定义结构, V1.1 由 KpiApi.getCurrentKpiResult 等方法填充.
 * <p>注: kpi_result 是宽表, id 保持 Long 类型与 DDL 一致.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiResultDTO {
    private Long id;
    private String empId;
    private String empName;
    /** MONTHLY/QUARTERLY/YEARLY. */
    private String cycleType;
    private LocalDate cycleDate;
    /** 计算基准日 (每日一算区分键). */
    private LocalDate asOfDate;
    /** 指标数据版本. */
    private String dataVersion;
    private String schemeCode;
    private String schemeName;
    /** KPI 总分. */
    private BigDecimal kpiTotalScore;
    /** 明细 JSON (可选). */
    private String detailJson;
    private LocalDateTime createTime;
}
