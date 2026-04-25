package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 绩效汇总入参 DTO（C.3 GET /perf-summary，Task M3.2.1）.
 *
 * <p>业务规则（plan L2013-L2019 + docs 03 §C.3）：
 * <ul>
 *   <li>dim ∈ {ORG / EMP}，V1.0 单条循环 KpiApi.getCurrentKpiTotal</li>
 *   <li>subjectIds.size &le; 100（08 §2.1 performance.max.subjects），超限抛 RPT-40007</li>
 *   <li>cycleType: MONTHLY / QUARTERLY / YEARLY（透传给 KpiApi）</li>
 * </ul>
 */
@Data
public class PerfSummaryReqDTO {

    /** 维度：EMP / ORG（必填） */
    @NotBlank(message = "dim 不能为空")
    private String dim;

    /** 对象 ID 列表（上限 100，由 Service 层校验抛 RPT-40007） */
    @NotEmpty(message = "subjectIds 不能为空")
    private List<String> subjectIds;

    /** KPI 周期类型：MONTHLY / QUARTERLY / YEARLY（必填） */
    @NotBlank(message = "cycleType 不能为空")
    private String cycleType;
}
