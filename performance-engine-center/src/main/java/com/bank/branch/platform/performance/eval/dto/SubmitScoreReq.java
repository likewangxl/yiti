package com.bank.branch.platform.performance.eval.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 规则驱动评价-提交打分请求体。
 */
@Data
public class SubmitScoreReq {
    /** 评价任务ID（必填）. */
    @NotNull
    private Long taskId;
    /** 被评价人明细ID（必填）. */
    @NotNull
    private Long targetId;
    /** 打分，范围 10~100（必填）. */
    @NotNull
    @Min(10)
    @Max(100)
    private Integer score;
}
