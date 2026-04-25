package com.bank.branch.platform.report.service;

import com.bank.branch.platform.report.dto.resp.QueryDimensionRespDTO;

/**
 * 报表元数据服务（A.1 维度+指标树）.
 *
 * <p>跨模块数据源：
 * <ul>
 *   <li>{@code performance-engine-center.MetricApi.listMetrics(baseDim, null)} — 指标定义</li>
 *   <li>{@code system-governance-center.DictApi.getDictLabel} — 维度 / 分组中文名</li>
 * </ul>
 */
public interface MetaService {

    /**
     * 按维度返回分组后的指标树.
     *
     * @param dim 维度 EMP / ORG / CUST（非法入参抛 RPT-40006）
     * @return 维度信息 + 分组树
     */
    QueryDimensionRespDTO getQueryDimensions(String dim);
}
