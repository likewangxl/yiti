package com.bank.branch.platform.performance.eval.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 待处理任务-批量提交打分请求体。
 */
@Data
public class SubmitBatchReq {
    /** 待提交明细分数列表（必填，至少一条）。 */
    @NotEmpty
    @Valid
    private List<SubmitReq> items;
}
