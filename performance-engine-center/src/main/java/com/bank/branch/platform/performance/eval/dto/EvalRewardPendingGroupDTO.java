package com.bank.branch.platform.performance.eval.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我的奖励分配待处理汇总行（当前登录人作为分配人，按部门聚合未提交明细）。
 */
@Data
public class EvalRewardPendingGroupDTO {
    /** 所属批次. */
    private Long batchId;
    /** 任务类型编码(固定 REWARD). */
    private String taskType;
    /** 任务名称(批次名称). */
    private String taskName;
    /** 任务类型展示名(字典翻译,如"奖励分配"). */
    private String taskTypeLabel;
    /** 部门名称. */
    private String dept;
    /** 该部门下待分配人数(submitted=0). */
    private Integer pendingCount;
    /** 分配合计(组内一致). */
    private BigDecimal assignTotal;
    /** 分配截止时间. */
    private LocalDateTime deadline;
}
