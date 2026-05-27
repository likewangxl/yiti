package com.bank.branch.platform.performance.eval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 评价任务表 EVAL_TASK 贫血实体.
 */
@Data
@TableName("EVAL_TASK")
public class EvalTask {
    /** 主键. */
    @TableId(value = "task_id", type = IdType.AUTO)
    private Long taskId;
    /** 任务名称. */
    private String taskName;
    /** 开始时间. */
    private LocalDateTime startTime;
    /** 截止时间. */
    private LocalDateTime endTime;
    /** 状态：0=进行中, 1=已结束. */
    private Integer status;
    /** 创建人 USER_ID. */
    private Long createBy;
    /** 创建时间. */
    private LocalDateTime createTime;
    /** 更新时间. */
    private LocalDateTime updateTime;
}
