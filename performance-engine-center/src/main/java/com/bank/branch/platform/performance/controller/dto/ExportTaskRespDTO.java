package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 导出任务响应 DTO（V1.2 Task Q6.4）.
 *
 * <p>Controller 层不直接返回 {@code PerfExportTask} entity（NoEntityInControllerArchTest 守护），
 * 所有字段通过装配转入 DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "导出任务信息")
public class ExportTaskRespDTO {

    @Schema(description = "任务 ID")
    private String id;

    @Schema(description = "导出类型（KPI/METRIC/ALLOC/DETAIL）")
    private String exportType;

    @Schema(description = "状态（PENDING/RUNNING/SUCCESS/FAILED）")
    private String status;

    @Schema(description = "MinIO object key（成功时）")
    private String fileKey;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "导出行数")
    private Integer rowCount;

    @Schema(description = "文件过期时间")
    private LocalDateTime expireAt;

    @Schema(description = "操作人")
    private String operatorId;

    @Schema(description = "错误信息（失败时）")
    private String errorMsg;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;
}
