package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 临时任务提交附件。 */
@Data
@TableName("RE_TASK_SUBMISSION_FILE")
public class ReTaskSubmissionFile {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long submissionId;
    private String fileObjectId;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private Integer sortNo;
    private String createdBy;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
}
