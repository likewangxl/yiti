package com.bank.branch.platform.performance.eval.dto;

import lombok.Data;

/**
 * 待处理任务明细行（处理某部门时展示的被打分人 + 打分入口）。
 */
@Data
public class EvalPendingItemDTO {
    /** 明细ID. */
    private Long itemId;
    /** 被打分人工号. */
    private String beEvalUserId;
    /** 被打分人姓名. */
    private String beEvalUserName;
    /** 被打分人部门. */
    private String beEvalDept;
    /** 被打分人标签. */
    private String beEvalTag;
    /** 权重标签. */
    private String weightTag;
    /** 评价类型（NUM=数值打分 / GRADE=等级打分），前端据此切换打分 UI. */
    private String scoreType;
    /** 已提交分数（未提交为 null）. */
    private Integer score;
    /** 是否已提交：0=未提交, 1=已提交. */
    private Integer submitted;
}
