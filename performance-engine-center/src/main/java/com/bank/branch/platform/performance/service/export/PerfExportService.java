package com.bank.branch.platform.performance.service.export;

import com.bank.branch.platform.performance.entity.PerfExportTask;

import java.util.Map;

/**
 * 绩效异步导出统一入口（V1.2 Task Q6.1）.
 *
 * <p>职责：
 * <ol>
 *   <li>为每次导出请求创建 {@link PerfExportTask}（初始 status=PENDING）</li>
 *   <li>根据 exportType 分发到对应 {@link ExportStrategy} 执行（V1.2 初期同步执行，
 *       V1.3 再接 @Async 切换异步）</li>
 *   <li>统一维护任务状态机：PENDING → RUNNING → SUCCESS/FAILED</li>
 *   <li>提供任务查询能力（含 operator 归属校验）</li>
 * </ol>
 *
 * <p>错误码：
 * <ul>
 *   <li>未知 exportType → {@code BIZ_KIND_INVALID (PERF-40002)}</li>
 *   <li>参数非法（exportType 空白 / operatorId 空白）→ {@code VALIDATION_FAILED (PERF-42200)}</li>
 *   <li>任务不存在 → {@code EXPORT_TASK_NOT_FOUND (PERF-42208)}</li>
 *   <li>非本人任务 → {@code EXPORT_TASK_OWNER_MISMATCH (PERF-42209)}</li>
 *   <li>导出行数超限 → {@code EXPORT_ROWS_EXCEEDS_LIMIT (PERF-42207)}</li>
 *   <li>文件生成失败 → {@code EXPORT_FILE_GENERATE_FAILED (PERF-50002)}</li>
 * </ul>
 */
public interface PerfExportService {

    /**
     * 创建导出任务并立即执行（V1.2 初期同步执行）.
     *
     * @param exportType 导出类型（KPI / METRIC / ALLOC / DETAIL）
     * @param params     导出参数（任意 JSON 可序列化 Map）
     * @param operatorId 操作人员工号（写入 operator_id，用于下载权限校验）
     * @return 任务主键 id
     */
    String createTask(String exportType, Map<String, Object> params, String operatorId);

    /**
     * 按主键查询任务（不做 owner 校验，消费方自行保证）.
     *
     * @param taskId 任务 ID
     * @return 任务实体
     */
    PerfExportTask getTask(String taskId);

    /**
     * 按主键查询任务 + 强制 owner 校验.
     *
     * @param taskId     任务 ID
     * @param operatorId 操作人员工号
     * @return 任务实体；operatorId 与任务归属不符时抛 {@code EXPORT_TASK_OWNER_MISMATCH}
     */
    PerfExportTask getTaskForOwner(String taskId, String operatorId);
}
