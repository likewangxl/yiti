package com.bank.branch.platform.performance.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 导入批次响应 DTO（V1.1 Task P5.5）.
 *
 * <p>Controller 不暴露 {@code PerfImportBatch} entity（NoEntityInControllerArchTest 守护），
 * 所有字段通过 {@code PerfImportBatch} 装配。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "导入批次信息")
public class PerfImportBatchRespDTO {

    /** 批次 ID（varchar 32 主键）. */
    @Schema(description = "批次 ID")
    private String id;

    /** 批次号（业务唯一键）. */
    @Schema(description = "批次号")
    private String batchNo;

    /** 导入类型：TARGET / BASE_DATA / ALLOC. */
    @Schema(description = "导入类型")
    private String importType;

    /** 原始文件名. */
    @Schema(description = "文件名")
    private String fileName;

    /** 状态：CREATED / RUNNING / SUCCESS / FAILED / DELETED. */
    @Schema(description = "状态")
    private String status;

    /** 总行数. */
    @Schema(description = "总行数")
    private Integer totalRows;

    /** 成功行数. */
    @Schema(description = "成功行数")
    private Integer successRows;

    /** 失败行数. */
    @Schema(description = "失败行数")
    private Integer errorRows;

    /** 备注 / 错误摘要. */
    @Schema(description = "备注（错误摘要或 JSON）")
    private String remark;

    /** 创建人. */
    @Schema(description = "创建人")
    private String createdBy;

    /** 创建时间. */
    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    /** 更新时间. */
    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;
}
