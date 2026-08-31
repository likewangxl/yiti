package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 任务提交状态转换审计历史。 */
@Data
@TableName("RE_TASK_STATUS_HISTORY")
public class ReTaskStatusHistory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long taskInstanceId;
    private Long assignmentId;
    private Long submissionId;
    private String actionCode;
    private String fromStatus;
    private String toStatus;
    private String opinion;
    private String operatorId;
    private LocalDateTime occurredAt;
    private LocalDateTime createTime;
}
