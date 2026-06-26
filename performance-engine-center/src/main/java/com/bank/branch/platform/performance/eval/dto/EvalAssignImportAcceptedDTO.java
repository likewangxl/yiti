package com.bank.branch.platform.performance.eval.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评价任务异步导入「受理」响应体。
 *
 * <p>导入接口由「同步原子返回结果/错误 CSV」改为「异步 + 前端轮询」后，
 * 接口线程仅完成解析 + 建 IMPORTING 批次，立即返回本 DTO：
 * 前端据 {@code batchId} 轮询 {@code GET /batches/{batchId}} 获取最终 status / errorSummary。</p>
 *
 * <p>{@code status} 取批次状态机值：受理时固定为 3（IMPORTING 处理中）。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvalAssignImportAcceptedDTO {

    /** 批次 ID（供前端轮询批次详情）。 */
    private Long batchId;

    /** 批次状态：受理时为 3（IMPORTING 处理中）。 */
    private Integer status;
}
