package com.bank.branch.platform.performance.eval.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一评价任务列表行投影 —— 合并 EVAL_TASK 与 EVAL_ASSIGN_BATCH 的 UNION ALL 结果。
 */
@Data
public class UnifiedEvalTaskRow {
    private Long sourceId;
    private String sourceType;    // AUTO / IMPORT
    private String taskType;      // EVAL / REWARD
    private String taskName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;       // 0=进行中 1=已结束 2=草稿
    private String createBy;
    private LocalDateTime createTime;
    private Long itemCount;       // 仅 batch 侧有值
}
