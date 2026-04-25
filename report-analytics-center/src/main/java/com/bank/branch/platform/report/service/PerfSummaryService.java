package com.bank.branch.platform.report.service;

import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.report.dto.req.PerfSummaryReqDTO;
import com.bank.branch.platform.report.dto.resp.ExportTaskRespDTO;
import com.bank.branch.platform.report.dto.resp.PerfSummaryRowVO;

/**
 * 绩效汇总报表服务（M3.2，汇总 C.3）.
 *
 * <p>规则：
 * <ul>
 *   <li>subjectIds.size &le; 100，超限抛 RPT-40007</li>
 *   <li>V1.0 单条循环调 {@link com.bank.branch.platform.performance.api.KpiApi#getCurrentKpiTotal}
 *       （V1.1 P4.3 已真实交付）</li>
 *   <li>上游异常包装 RPT-50001（R4 fail-close）</li>
 * </ul>
 *
 * <p>缓存：{@code rpt:summary:perf} TTL=5min，按 dim + cycleType + subjectIds hash 组合 key.
 */
public interface PerfSummaryService {

    /**
     * 查询绩效汇总（分页）.
     *
     * @param req  入参（dim/subjectIds/cycleType）
     * @param page 分页参数
     * @return 绩效汇总分页结果
     */
    PageResult<PerfSummaryRowVO> getPerfSummary(PerfSummaryReqDTO req, PageRequest page);

    /**
     * 提交绩效汇总异步导出任务（M3.2.2，M5 Worker 启用）.
     */
    ExportTaskRespDTO submitPerfSummaryExport(PerfSummaryReqDTO req);
}
