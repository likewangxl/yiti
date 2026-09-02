package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/** 正式触达任务实体，对应 MARKETING_TOUCH_TASK。 */
@Data
@TableName("MARKETING_TOUCH_TASK")
public class TouchTask {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private Long custId;
    private String sourceType;
    private Long sourceBizId;
    private String orgId;
    private String assigneeEmpId;
    private String taskType;
    private String taskStatus;
    private LocalDateTime planFinishTime;
    private LocalDateTime warningTime;
    private String slaStatus;
    private LocalDateTime successTime;
    private LocalDateTime cancelTime;
    private String cancelReason;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    @Version
    private Integer lockVersion;
}
