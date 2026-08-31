package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bank.branch.platform.redengine.api.dto.ReTaskExportStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** 任务异步 ZIP/Excel 导出作业。 */
@Data
@TableName("RE_TASK_EXPORT_TASK")
public class ReTaskExportTask {

    /** 平台作业 ID 为字符串，便于异步接口安全暴露。 */
    @TableId(type = IdType.INPUT)
    private String id;
    private Long taskId;
    private String operatorId;
    /** 选中的 RE_ITEM_CODE 编码 JSON。 */
    private String detailItemCodesJson;
    private ReTaskExportStatus status;
    private Integer rowCount;
    private Integer sheetCount;
    /** 单个 Sheet 最大数据行数，数据库约束为 5000。 */
    private Integer sheetRowLimit;
    private String fileObjectId;
    private Long fileSize;
    private LocalDateTime expireAt;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
