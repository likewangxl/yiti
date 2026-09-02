package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 员工维度的任务待办快照。 */
@Data
@TableName("RE_TASK_TODO")
public class ReTaskTodo {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assignmentId;
    private String employeeId;
    private String roleCode;
    private String status;
    private LocalDateTime availableAt;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
