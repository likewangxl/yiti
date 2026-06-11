package com.bank.branch.platform.performance.eval.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 我的待处理任务汇总行（当前登录人作为打分人，按被打分人部门聚合未提交明细）。
 */
@Data
public class EvalPendingGroupDTO {
    /** 所属批次. */
    private Long batchId;
    /** 任务类型编码（EVAL/REWARD）. */
    private String taskType;
    /** 任务名称（批次名称）. */
    private String taskName;
    /** 任务类型展示名（字典翻译，如"评价任务"）. */
    private String taskTypeLabel;
    /** 被打分人部门. */
    private String dept;
    /** 该部门下待评价人数（submitted=0）. */
    private Integer pendingCount;
    /** 打分截止时间. */
    private LocalDateTime deadline;
}
