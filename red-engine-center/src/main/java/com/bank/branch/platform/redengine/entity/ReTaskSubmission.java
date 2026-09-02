package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bank.branch.platform.redengine.api.dto.ReTaskSubmissionStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** 党支部级任务提交，一条分配在同一时间只保留一份当前提交。 */
@Data
@TableName("RE_TASK_SUBMISSION")
public class ReTaskSubmission {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long taskInstanceId;
    private Long assignmentId;
    private Long branchId;
    private Integer versionNo;
    private ReTaskSubmissionStatus status;
    private String dimensionCode;
    private String itemCode;
    /** 临时任务正文；四维材料由关联的 RE_SUBMIT 保存。 */
    private String contentText;
    /** 四维或扩展任务结构化数据。 */
    private String formData;
    private String submitterId;
    private LocalDateTime submittedAt;
    private String branchReviewerId;
    private LocalDateTime branchReviewedAt;
    private String orgReviewerId;
    private LocalDateTime orgReviewedAt;
    private String reviewOpinion;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
