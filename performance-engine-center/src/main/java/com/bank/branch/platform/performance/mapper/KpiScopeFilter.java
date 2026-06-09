package com.bank.branch.platform.performance.mapper;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * KPI 计分结果数据范围过滤（考核计算 KPI_CALC BizType 的数据范围落到 PERF_KPI_SCORE 的 subject 上）.
 *
 * <p>{@code scopeAll=true} → 不过滤（全部）。否则按对象类型分支：
 * ORG 对象 {@code subject_id ∈ orgCodes}（本机构/本机构+下级）；
 * EMP 对象 {@code subject_id ∈ empIds}（本人 或 机构下属员工）；
 * 两者皆空 → 无任何对象（fail-close）。CUST 对象不在 本人/本机构 范围内，仅 全部 可见。
 */
@Data
@AllArgsConstructor
public class KpiScopeFilter {

    /** true=全部，不过滤. */
    private boolean scopeAll;

    /** 允许的员工工号（subject_type=EMP）. */
    private List<String> empIds;

    /** 允许的机构编码（subject_type=ORG）. */
    private List<String> orgCodes;

    /** 全部范围. */
    public static KpiScopeFilter all() {
        return new KpiScopeFilter(true, List.of(), List.of());
    }

    /** 受限范围（员工 / 机构允许集；皆空即 fail-close）. */
    public static KpiScopeFilter of(List<String> empIds, List<String> orgCodes) {
        return new KpiScopeFilter(false,
                empIds == null ? List.of() : empIds,
                orgCodes == null ? List.of() : orgCodes);
    }
}
