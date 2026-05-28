package com.bank.branch.platform.performance.eval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 评价关系规则主表 EVAL_RULE 贫血实体.
 */
@Data
@TableName("EVAL_RULE")
public class EvalRule {
    /** 主键. */
    @TableId(value = "rule_id", type = IdType.AUTO)
    private Long ruleId;
    /** 规则名称. */
    private String ruleName;
    /** 被评价人标签ID. */
    private Long beEvalTagId;
    /** 状态：1=启用, 0=停用. */
    private Integer status;
    /** 创建时间. */
    private LocalDateTime createTime;
    /** 更新时间. */
    private LocalDateTime updateTime;
}
