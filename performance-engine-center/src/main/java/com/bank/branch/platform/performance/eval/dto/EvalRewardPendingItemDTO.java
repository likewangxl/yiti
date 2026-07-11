package com.bank.branch.platform.performance.eval.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 奖励分配明细行（处理某部门时展示的被分配人 + 分配值录入）。
 */
@Data
public class EvalRewardPendingItemDTO {
    /** 明细ID. */
    private Long itemId;
    /** 被分配人工号(快照). */
    private String beAssignedUserId;
    /** 被分配人姓名. */
    private String beAssignedUserName;
    /** 部门名称. */
    private String deptName;
    /** 原始值(展示). */
    private BigDecimal originalValue;
    /** 兑现值(展示). */
    private BigDecimal cashValue;
    /** 分配合计(该组一致). */
    private BigDecimal assignTotal;
    /** 分配值(已提交则回填,未提交为 null). */
    private BigDecimal assignValue;
    /** 是否已提交:0未提交/1已提交. */
    private Integer submitted;
}
