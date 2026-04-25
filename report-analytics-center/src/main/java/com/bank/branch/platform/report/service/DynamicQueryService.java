package com.bank.branch.platform.report.service;

import com.bank.branch.platform.report.dto.req.DynamicQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.DynamicQueryRespDTO;

/**
 * 动态查询服务（A.2 POST /api/reports/dynamic-query）.
 *
 * <p>三层守护：
 * <ul>
 *   <li>入参上限：subjectIds ≤ 100（RPT-40007） / metricCodes ≤ 20（RPT-40008）</li>
 *   <li>外层 DataScope：{@code BizScopeApi.buildScopeContext(empId, REPORT, LIST)}，
 *       请求对象不在范围内 → RPT-40005</li>
 *   <li>内层 MetricApi：V1.0 单条循环按 dim 调 getEmpMetricValues / getOrgMetricValues / getCustMetricValues</li>
 * </ul>
 */
public interface DynamicQueryService {

    /**
     * 执行动态查询。
     *
     * @param req 查询入参
     * @return 查询结果（列定义 + 行数据）
     */
    DynamicQueryRespDTO execute(DynamicQueryReqDTO req);
}
