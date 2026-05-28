package com.bank.branch.platform.performance.eval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 评价人组明细表 EVAL_RULE_GROUP 贫血实体.
 */
@Data
@TableName("EVAL_RULE_GROUP")
public class EvalRuleGroup {
    /** 主键. */
    @TableId(value = "group_id", type = IdType.AUTO)
    private Long groupId;
    /** 所属规则ID. */
    private Long ruleId;
    /** 组类型：1=按标签选人, 2=部门员工组. */
    private Integer groupType;
    /** type=1 时的评价人标签ID. */
    private Long evalTagId;
    /** 权重百分比（如 50.00）. */
    private BigDecimal weight;
    /** 排序. */
    private Integer sortOrder;
    /** 评分方式：1=数值打分, 2=等级打分. */
    private Integer scoreMode;
}
