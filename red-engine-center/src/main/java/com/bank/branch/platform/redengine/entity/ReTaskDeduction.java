package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bank.branch.platform.redengine.api.dto.ReTaskDeductionStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 任务逾期扣分记录，允许完全未上报时 submissionId 为空。 */
@Data
@TableName("RE_TASK_DEDUCTION")
public class ReTaskDeduction {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long taskInstanceId;
    private Long assignmentId;
    /** 扣分归属党支部。 */
    private Long branchId;
    private BigDecimal deductionPoints;
    private String deductionReason;
    private ReTaskDeductionStatus status;
    private String executedBy;
    private LocalDateTime executedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
