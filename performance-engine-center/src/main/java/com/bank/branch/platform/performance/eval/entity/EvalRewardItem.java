package com.bank.branch.platform.performance.eval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 奖励分配明细 EVAL_REWARD_ITEM 贫血实体。
 * <p>每行为一个被分配人，含分配人归属、导入快照(原始值/兑现值/分配合计)与提交后的分配值。</p>
 */
@Data
@TableName("EVAL_REWARD_ITEM")
public class EvalRewardItem {
    /** 主键. */
    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;
    /** 所属批次. */
    private Long batchId;
    /** 分配人工号(归一化后的 USER_ID). */
    private String assignUserId;
    /** 被分配人工号(原样快照,不校验). */
    private String beAssignedUserId;
    /** 被分配人姓名(快照). */
    private String beAssignedUserName;
    /** 部门名称(分组键,空存空串). */
    private String deptName;
    /** 原始值(展示). */
    private BigDecimal originalValue;
    /** 兑现值(展示). */
    private BigDecimal cashValue;
    /** 分配合计(组内一致,分配目标池). */
    private BigDecimal assignTotal;
    /** 分配值(提交时填入). */
    private BigDecimal assignValue;
    /** 是否已提交:0未提交/1已提交. */
    private Integer submitted;
    /** 提交时间. */
    private LocalDateTime submitTime;

    /**
     * 分配人登录名(PT_USER.username,即工号),非表字段。
     * <p>仅管理端批次详情展示用:由 Service 经 UserApi 按 assign_user_id 反查填充。</p>
     */
    @TableField(exist = false)
    private String assignUserUsername;
}
