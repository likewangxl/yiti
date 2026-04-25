package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 触达任务汇总入参 DTO（C.2 GET /touch-task-summary，Task M3.1.1）.
 *
 * <p>业务规则（plan L1855-L1861 + docs 03 §C.2 + 09 §3.3）：
 * <ul>
 *   <li>startDate / endDate 必填，范围 &le; 1 年（366 天）上限由 Service 层校验抛 RPT-40006</li>
 *   <li>orgId 可选：为空时按当前用户 orgCode 兜底（BizScopeApi 生成 DataScopeContext）</li>
 * </ul>
 */
@Data
public class TouchSummaryReqDTO {

    /** 统计开始日期（必填） */
    @NotNull(message = "startDate 不能为空")
    private LocalDate startDate;

    /** 统计结束日期（必填） */
    @NotNull(message = "endDate 不能为空")
    private LocalDate endDate;

    /** 机构 ID（可选，为空时按当前用户主机构兜底） */
    private String orgId;
}
