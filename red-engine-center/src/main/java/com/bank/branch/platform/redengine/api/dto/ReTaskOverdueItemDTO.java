package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 任务逾期待执行扣分条目。 */
@Data
@Schema(description = "红色引擎任务逾期待执行扣分条目")
public class ReTaskOverdueItemDTO {

    private Long taskId;
    private Long taskInstanceId;
    private Long assignmentId;
    private String taskType;
    private String taskName;
    /** 任务说明；完全未上报时也以此字段作为任务内容。 */
    private String taskContent;
    private Long branchId;
    private String branchName;
    private LocalDateTime taskStartAt;
    private LocalDateTime taskEndAt;
    /** 无提交版本的 assignment 标记，供首页明确显示“--”。 */
    private Boolean isUnreported;
    /** 完全未上报时为空，序列化为 null 由前端显示“--”。 */
    private String submitterId;
    private String submitterName;
    private LocalDateTime submittedAt;
    private BigDecimal deductionPoints;
    private ReTaskDeductionStatus deductionStatus;

    /** 兼容旧前端字段命名。 */
    public String getTaskTitle() {
        return taskName;
    }

    /** 兼容旧前端字段命名。 */
    public void setTaskTitle(String taskTitle) {
        this.taskName = taskTitle;
    }

    /** 兼容前端“任务内容”字段命名。 */
    public String getContent() {
        return taskContent;
    }

    /** 兼容前端“任务内容”字段命名。 */
    public void setContent(String content) {
        this.taskContent = content;
    }
}
