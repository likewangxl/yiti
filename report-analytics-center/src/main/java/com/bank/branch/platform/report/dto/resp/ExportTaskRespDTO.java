package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 异步导出任务响应 DTO（A.3 / J 章导出系列，Task M1.3.1 起占位）.
 *
 * <p>V1.0 M1.3 仅返回创建后的任务 ID 和当前状态（PENDING）；M5 全链路 Worker 落地后
 * 同结构会承载 status=SUCCESS + fileKey 等终态字段。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExportTaskRespDTO {

    /** 任务 ID */
    private String taskId;

    /** 状态：PENDING/RUNNING/SUCCESS/FAILED */
    private String status;
}
