package com.bank.branch.platform.performance.eval.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 待处理任务-提交单条打分请求体。
 */
@Data
public class SubmitReq {
    /** 明细ID（必填）。 */
    @NotNull
    private Long itemId;
    /** 分数（必填；数值 10~100 或等级预设值，由 service 按评价类型校验）。 */
    @NotNull
    private Integer score;
}
