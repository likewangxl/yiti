package com.bank.branch.platform.performance.service.importer;

import com.bank.branch.platform.performance.controller.dto.PerfImportBatchRespDTO;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 绩效数据导入统一入口（V1.1 Task P5.1）.
 *
 * <p>职责：
 * <ol>
 *   <li>为每次上传创建 {@link PerfImportBatch}（初始 status=CREATED）</li>
 *   <li>根据 importType 分发到对应 {@link ImportStrategy}</li>
 *   <li>统一维护批次状态机：CREATED → RUNNING → SUCCESS/FAILED</li>
 *   <li>提供批次查询、错误明细查询、重试、删除等管理能力</li>
 * </ol>
 *
 * <p>错误码：
 * <ul>
 *   <li>未知 importType → {@code BIZ_KIND_INVALID (PERF-40002)}</li>
 *   <li>参数非法（file 空、importType 空白）→ {@code VALIDATION_FAILED (PERF-42200)}</li>
 *   <li>批次不存在 → {@code IMPORT_BATCH_NOT_FOUND (PERF-40017)}</li>
 * </ul>
 */
public interface PerfImportService {

    /**
     * 启动导入：创建批次、分发到策略、更新终态.
     *
     * @param importType 导入类型（TARGET / BASE_DATA / ALLOC / METRIC_DEF / METRIC_RESULT）
     * @param file       Excel 文件（不得为 null/空）
     * @param operatorId 操作人员工号（写入 createdBy）
     * @param dataDate   数据日期：METRIC_RESULT / KPI_SCORE 必填，其他类型忽略；
     *                   缺失/格式错由 Controller 层 fail-fast
     * @param schemeCode KPI 方案编码：仅 KPI_SCORE 必填（页面方案下拉传入），其他类型忽略
     * @return 新建批次的主键 id
     */
    String startImport(String importType, MultipartFile file, String operatorId,
                       LocalDate dataDate, String schemeCode);

    /**
     * 按主键查询批次，不存在时抛 {@code IMPORT_BATCH_NOT_FOUND}.
     *
     * @param batchId 批次 ID
     * @return 批次实体
     */
    PerfImportBatch getBatch(String batchId);

    /**
     * 查询该批次的错误明细（每行 "第 N 行: xxx"）.
     *
     * <p>当 {@code errorFileObjectId} 不空时，可选：返回 MinIO 下载 URL；本期简化为
     * 直接解析 {@code remark}/errorSummary。
     *
     * @param batchId 批次 ID
     * @return 错误行列表（空列表表示无错误）
     */
    List<String> getErrorDetails(String batchId);

    /**
     * 重试导入（仅 FAILED 状态批次可重试）.
     *
     * <p>V1.1 简化实现：仅把状态复位为 CREATED，实际重跑由调用方重新上传或后续迭代实现.
     *
     * @param batchId 批次 ID
     */
    void retry(String batchId);

    /**
     * 删除批次（仅终态 SUCCESS/FAILED 可删）.
     *
     * @param batchId 批次 ID
     */
    void delete(String batchId);

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本，内部调用 {@link #getBatch}，再装配成
     * {@link PerfImportBatchRespDTO}.
     *
     * @param batchId 批次 ID
     * @return 响应 DTO（不存在则抛 IMPORT_BATCH_NOT_FOUND）
     */
    PerfImportBatchRespDTO getBatchDto(String batchId);
}
