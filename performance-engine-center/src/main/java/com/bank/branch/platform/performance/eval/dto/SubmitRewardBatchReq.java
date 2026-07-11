package com.bank.branch.platform.performance.eval.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 奖励分配-批量提交请求体（一次性提交某部门下全部被分配人的分配值）。
 */
@Data
public class SubmitRewardBatchReq {
    /** 批次ID(必填). */
    @NotNull
    private Long batchId;
    /** 部门名称(必填,分组键). */
    @NotNull
    private String dept;
    /** 分配明细(必填,至少一条). */
    @NotEmpty
    @Valid
    private List<Entry> items;

    /** 单条分配:明细ID + 分配值. */
    @Data
    public static class Entry {
        @NotNull
        private Long itemId;
        @NotNull
        private BigDecimal assignValue;
    }
}
