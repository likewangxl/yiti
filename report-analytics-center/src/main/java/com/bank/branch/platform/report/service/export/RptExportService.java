package com.bank.branch.platform.report.service.export;

import com.bank.branch.platform.report.entity.RptExportTask;

import java.util.Map;

/**
 * 报表异步导出统一入口（Task M5.2.2）.
 *
 * <p>职责：
 * <ol>
 *   <li>为每次导出请求创建 {@link RptExportTask}（status=PENDING）</li>
 *   <li>根据 exportType 分发到对应 {@link ExportStrategy} 执行（V1.0 同步执行，
 *       V1.1+ 计划切异步线程池）</li>
 *   <li>统一维护任务状态机：PENDING → RUNNING → SUCCESS/FAILED/CANCELLED</li>
 *   <li>提供任务查询能力（含 operator 归属校验）</li>
 *   <li>支持 PENDING/RUNNING 取消（仅本人）</li>
 * </ol>
 *
 * <p>错误码：
 * <ul>
 *   <li>未知 exportType / 参数空 → {@code EXPORT_START_FAILED (RPT-50003)}</li>
 *   <li>任务不存在 → {@code EXPORT_TASK_NOT_FOUND (RPT-40009)}</li>
 *   <li>非本人任务 → {@code EXPORT_DOWNLOAD_FORBIDDEN (RPT-42209)}</li>
 *   <li>取消已终态任务 → {@code EXPORT_TASK_NOT_READY (RPT-40010)}</li>
 * </ul>
 *
 * <p>对照 performance-engine-center 的 {@code PerfExportService}，保持跨模块一致风格.
 */
public interface RptExportService {

    /**
     * 创建导出任务并立即执行（V1.0 同步）.
     *
     * @param exportType DYNAMIC_QUERY / TOUCH_SUMMARY / PERF_SUMMARY / CUSTPOOL_SUMMARY
     * @param params     导出参数（任意 JSON 可序列化 Map）
     * @param operatorId 操作人员工号（写入 operator_id，下载/取消归属校验依赖此字段）
     * @return 任务主键 id
     */
    String createTask(String exportType, Map<String, Object> params, String operatorId);

    /**
     * 按主键查询任务（不做归属校验，调用方自行保证）.
     *
     * @throws com.bank.branch.platform.common.web.exception.BizException RPT-40009
     */
    RptExportTask getTask(String taskId);

    /**
     * 按主键查询任务 + 强制归属校验.
     *
     * @throws com.bank.branch.platform.common.web.exception.BizException RPT-40009 / RPT-42209
     */
    RptExportTask getTaskForOwner(String taskId, String operatorId);

    /**
     * 取消任务（仅 PENDING/RUNNING/CANCELLED 可重入；SUCCESS/FAILED 已终态拒绝）.
     *
     * @throws com.bank.branch.platform.common.web.exception.BizException RPT-40009 / RPT-42209 / RPT-40010
     */
    void cancelTask(String taskId, String operatorId);
}
