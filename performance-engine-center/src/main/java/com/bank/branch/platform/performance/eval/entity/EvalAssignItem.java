package com.bank.branch.platform.performance.eval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待处理任务明细 EVAL_ASSIGN_ITEM 贫血实体.
 * <p>每行为「打分人×被打分人」的一条显式配对，含双方导入快照、权重标签、评价类型与打分。</p>
 */
@Data
@TableName("EVAL_ASSIGN_ITEM")
public class EvalAssignItem {
    /** 主键. */
    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;
    /** 所属批次. */
    private Long batchId;
    /** 打分人工号. */
    private String evalUserId;
    /** 打分人姓名（导入快照）. */
    private String evalUserName;
    /** 打分人标签（导入快照）. */
    private String evalUserTag;
    /** 打分人部门（导入快照）. */
    private String evalUserDept;
    /** 被打分人工号. */
    private String beEvalUserId;
    /** 被打分人姓名（导入快照）. */
    private String beEvalUserName;
    /** 被打分人部门（导入快照，分组键，空存空串）. */
    private String beEvalDept;
    /** 被打分人标签（导入快照）. */
    private String beEvalTag;
    /** 权重标签（字典 EVAL_WEIGHT_TAG）. */
    private String weightTag;
    /** 评价类型（字典 EVAL_SCORE_TYPE：NUM/GRADE）. */
    private String scoreType;
    /** 打分（提交后填入）. */
    private Integer score;
    /** 是否已提交：0=未提交, 1=已提交. */
    private Integer submitted;
    /** 提交时间. */
    private LocalDateTime submitTime;

    /**
     * 打分人登录名（PT_USER.username，即工号），非表字段。
     * <p>仅批次详情展示用：由 Service 经 UserApi 按 eval_user_id(USER_ID) 反查填充。</p>
     */
    @TableField(exist = false)
    private String evalUserUsername;
    /**
     * 被打分人登录名（PT_USER.username，即工号），非表字段。
     * <p>仅批次详情展示用：由 Service 经 UserApi 按 be_eval_user_id(USER_ID) 反查填充。</p>
     */
    @TableField(exist = false)
    private String beEvalUserUsername;
}
