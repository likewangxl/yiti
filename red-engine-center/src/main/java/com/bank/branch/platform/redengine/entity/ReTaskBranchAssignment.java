package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 任务实例在党支部维度的分配快照。 */
@Data
@TableName("RE_TASK_BRANCH_ASSIGNMENT")
public class ReTaskBranchAssignment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskInstanceId;
    private Long branchId;
    /** UNREPORTED/BRANCH_PENDING/ORG_PENDING/APPROVED/REJECTED_BY_* / CLOSED。 */
    private String status;
    /** 当前提交版本号，重提复用同一 assignment。 */
    private Integer currentVersion;
    private LocalDateTime lastSubmittedAt;
    private String lastSubmitterId;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
