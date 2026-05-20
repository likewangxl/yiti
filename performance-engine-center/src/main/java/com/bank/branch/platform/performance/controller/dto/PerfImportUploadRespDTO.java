package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * V1.11：导入上传同步响应 DTO.
 *
 * <p>{@code POST /api/perf/import/upload} 返回字段集，相比原 {@code String batchId}
 * 增加 totalRows / insertedRows / updatedRows / errorRows 四个计数项，
 * 前端可在 toast 中直接展示 "新增 X 条 / 更新 Y 条" 等信息。
 *
 * <p>对非 METRIC_DEF 导入类型（TARGET / BASE_DATA / ALLOC），
 * {@code insertedRows = successRows} 且 {@code updatedRows = 0}（与既有语义兼容）.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "导入上传同步响应 DTO（V1.11）")
public class PerfImportUploadRespDTO {

    @Schema(description = "批次 ID")
    private String batchId;

    @Schema(description = "总行数")
    private Integer totalRows;

    @Schema(description = "新增行数 (= successRows - updatedRows)")
    private Integer insertedRows;

    @Schema(description = "更新行数（V1.11：仅 METRIC_DEF 导入使用，其他类型恒 0）")
    private Integer updatedRows;

    @Schema(description = "失败行数")
    private Integer errorRows;

    /**
     * 错误明细摘要（与 {@link PerfImportBatchRespDTO#getRemark()} 同源）.
     *
     * <p>2026-05-19 微调：把行级 errorSummary 同步透传到 upload 响应，
     * 前端拿到 {@code errorRows > 0} 时可直接弹出此字段而不需要二次请求
     * {@code GET /api/perf/import/batches/{id}}。多行错误用 "; " 分隔，
     * 超长 (>3900 字符) 由 Service 端 truncate。
     */
    @Schema(description = "错误明细摘要（多行用分号分隔，前端可直接展示给用户）")
    private String errorSummary;
}
