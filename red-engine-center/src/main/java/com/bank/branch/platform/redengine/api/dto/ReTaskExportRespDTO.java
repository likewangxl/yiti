package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 任务异步导出作业响应。 */
@Data
@Schema(description = "任务异步导出作业")
public class ReTaskExportRespDTO {

    private String exportId;
    private Long taskId;

    /** 对外状态：QUEUED/RUNNING/SUCCEEDED/FAILED。 */
    private String status;

    /** 导出的数据行数，不包含 Excel 表头。 */
    private Integer totalRows;
    /** 与持久化字段同名的兼容返回字段；与 totalRows 数值一致。 */
    private Integer rowCount;
    private Integer sheetCount;
    private Integer sheetRowLimit;
    private Long fileSize;
    private LocalDateTime expireAt;
    private String errorMessage;
    private Boolean downloadable;
}
