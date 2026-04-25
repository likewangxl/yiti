package com.bank.branch.platform.report.service;

import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.report.dto.req.CustPoolSummaryReqDTO;
import com.bank.branch.platform.report.dto.resp.CustPoolSummaryVO;
import com.bank.branch.platform.report.dto.resp.ExportTaskRespDTO;

/**
 * 客户池统计报表服务（M3.3，汇总 C.4）.
 *
 * <p>规则：
 * <ul>
 *   <li>未传 orgId 时按当前用户 orgCode 兜底</li>
 *   <li>调 customer.CustomerQueryApi.countCustomers，构造 4 次 filter（total/VIP/普通/潜在）</li>
 *   <li>上游异常包装 RPT-50001（R4 fail-close）</li>
 * </ul>
 *
 * <p>缓存：{@code rpt:summary:cust} TTL=5min.
 */
public interface CustPoolSummaryService {

    /**
     * 查询客户池汇总（分页）.
     *
     * @param req  入参（orgId 可选）
     * @param page 分页参数
     * @return 客户池汇总分页结果
     */
    PageResult<CustPoolSummaryVO> getCustPoolSummary(CustPoolSummaryReqDTO req, PageRequest page);

    /**
     * 提交客户池汇总异步导出任务（M3.3.2，M5 Worker 启用）.
     */
    ExportTaskRespDTO submitCustPoolSummaryExport(CustPoolSummaryReqDTO req);
}
