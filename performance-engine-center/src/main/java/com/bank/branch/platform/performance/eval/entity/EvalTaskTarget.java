package com.bank.branch.platform.performance.eval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 任务被评价人明细表 EVAL_TASK_TARGET 贫血实体.
 */
@Data
@TableName("EVAL_TASK_TARGET")
public class EvalTaskTarget {
    /** 主键. */
    @TableId(value = "target_id", type = IdType.AUTO)
    private Long targetId;
    /** 所属任务ID. */
    private Long taskId;
    /** 被评价人 USER_ID（工号，String）. */
    private String beEvalUserId;
    /** 快照的规则ID. */
    private Long ruleId;
    /** 最终得分（计算后填入）. */
    private BigDecimal finalScore;
    /** 当前评价人适用的评分方式（非DB列，由接口动态填充）. */
    @TableField(exist = false)
    private Integer scoreMode;
}
