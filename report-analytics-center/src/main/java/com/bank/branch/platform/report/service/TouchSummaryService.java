package com.bank.branch.platform.report.service;

import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.report.dto.req.TouchSummaryReqDTO;
import com.bank.branch.platform.report.dto.resp.ExportTaskRespDTO;
import com.bank.branch.platform.report.dto.resp.ReportTouchOrgVO;

/**
 * 触达任务监控报表服务（M3.1，汇总 C.2）.
 *
 * <p>规则：
 * <ul>
 *   <li>入参日期范围 &le; 366 天（08 §2.1 max.date.range.days），超限 RPT-40006</li>
 *   <li>未传 orgId 时按当前用户主机构兜底</li>
 *   <li>上游 {@code customer.TouchTaskQueryApi.getOrgTouchSummary} 调用失败 → RPT-50001（R4 fail-close）</li>
 * </ul>
 *
 * <p>缓存：{@code rpt:summary:touch} TTL=5min，按 orgId + startDate + endDate 组合 key.
 */
public interface TouchSummaryService {

    /**
     * 查询机构触达汇总（分页）.
     *
     * @param req  入参（startDate/endDate/orgId）
     * @param page 分页参数
     * @return 机构触达汇总分页结果
     */
    PageResult<ReportTouchOrgVO> getOrgTouchSummary(TouchSummaryReqDTO req, PageRequest page);

    /**
     * 提交触达汇总异步导出任务（M3.1.2，M5 Worker 启用）.
     *
     * @param req 同 view 入参
     * @return 任务 ID + PENDING 状态
     */
    ExportTaskRespDTO submitTouchSummaryExport(TouchSummaryReqDTO req);
}
