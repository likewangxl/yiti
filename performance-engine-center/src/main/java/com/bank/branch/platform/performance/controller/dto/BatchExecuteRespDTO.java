package com.bank.branch.platform.performance.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 指标批量执行聚合响应. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchExecuteRespDTO {
    /** 请求指标总数. */
    private int total;
    /** 成功数. */
    private int success;
    /** 失败数. */
    private int failed;
    /** 逐指标结果明细. */
    private List<Item> results;

    /** 单指标执行结果. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        /** 指标编码. */
        private String metricCode;
        /** 状态：SUCCESS/RUNNING/FAILED（失败为本地聚合状态）. */
        private String status;
        /** 成功时的 run_task 主键（失败为 null）. */
        private String runTaskId;
        /** 失败原因（成功为 null）. */
        private String errorMsg;
    }
}
