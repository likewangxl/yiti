package com.bank.branch.platform.report.service;

import com.bank.branch.platform.report.dto.req.DynamicQueryReqDTO;
import com.bank.branch.platform.report.dto.req.TouchSummaryReqDTO;
import com.bank.branch.platform.report.dto.resp.ExportTaskRespDTO;

/**
 * 异步导出任务服务（M1.3/M3 占位，M5 落地完整 Worker 链路）.
 *
 * <p>V1.0 现阶段行为：仅生成 PENDING 任务记录 + 返回 taskId，
 * 不真正执行导出（Worker 在 M5 启用）。
 */
public interface ExportTaskService {

    /**
     * 创建动态查询导出任务（M1.3）.
     *
     * @param req 入参（与 A.2 同款）
     * @return 任务 ID + PENDING 状态
     */
    ExportTaskRespDTO submitDynamicQueryExport(DynamicQueryReqDTO req);

    /**
     * 创建触达汇总导出任务（M3.1.2）.
     *
     * @param req 入参（与 C.2 同款）
     * @return 任务 ID + PENDING 状态
     */
    ExportTaskRespDTO submitTouchSummaryExport(TouchSummaryReqDTO req);
}
