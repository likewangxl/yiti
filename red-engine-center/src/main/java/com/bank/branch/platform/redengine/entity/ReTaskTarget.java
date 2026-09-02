package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 任务定义的对象配置。 */
@Data
@TableName("RE_TASK_TARGET")
public class ReTaskTarget {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    /** RE_TASK_TARGET.TARGET_TYPE：BRANCH 或 EMPLOYEE。 */
    private String targetType;
    /** 指定党支部时填写。 */
    private Long branchId;
    /** 指定员工时填写。 */
    private String employeeId;
    /** 规范化目标键，例如 BRANCH:1、EMPLOYEE:user-1。 */
    private String targetKey;
    /** 发布时保存的目标展示快照。 */
    private String targetLabel;
    private String createdBy;
    private LocalDateTime createTime;
}
