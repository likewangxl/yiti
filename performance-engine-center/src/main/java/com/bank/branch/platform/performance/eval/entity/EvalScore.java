package com.bank.branch.platform.performance.eval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 评价打分记录表 EVAL_SCORE 贫血实体.
 */
@Data
@TableName("EVAL_SCORE")
public class EvalScore {
    /** 主键. */
    @TableId(value = "score_id", type = IdType.AUTO)
    private Long scoreId;
    /** 任务ID. */
    private Long taskId;
    /** 关联 EVAL_TASK_TARGET.TARGET_ID. */
    private Long targetId;
    /** 评价人 USER_ID（工号，String）. */
    private String evalUserId;
    /** 所属评价人组ID. */
    private Long groupId;
    /** 打分 10~100. */
    private Integer score;
    /** 提交时间. */
    private LocalDateTime submitTime;
}
