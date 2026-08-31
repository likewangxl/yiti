package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bank.branch.platform.redengine.api.dto.ReTaskDimensionProgressStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** 四大维度任务在支部 assignment 上的累计上传进度。 */
@Data
@TableName("RE_TASK_DIMENSION_PROGRESS")
public class ReTaskDimensionProgress {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long taskInstanceId;
    private Long assignmentId;
    /** 维度编码，取任务域定义的维度值。 */
    private String dimensionCode;
    private ReTaskDimensionProgressStatus status;
    /** 同一季度/实例内该维度累计有效上传次数。 */
    private Integer uploadCount;
    private LocalDateTime firstUploadedAt;
    private LocalDateTime completedAt;
    private Long completedSubmissionId;
    private Long lastSubmissionId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
