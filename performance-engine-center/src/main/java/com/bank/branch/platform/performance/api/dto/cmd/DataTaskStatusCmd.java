package com.bank.branch.platform.performance.api.dto.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 外部数据任务状态上报 Cmd.
 * <p>V1.0 仅定义结构, V1.1 由 DataTaskApi.reportDataTaskStatus 消费.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataTaskStatusCmd {
    /** 外部系统的任务 ID. */
    private String taskId;
    /** 数据类型: ALLOC_RELATION / EMP_INDEX_RESULT / ORG_INDEX_RESULT / CUST_INDEX_RESULT. */
    private String dataType;
    private LocalDate dataDate;
    private String version;
    /** 状态: SUCCESS / FAILED. */
    private String status;
    /** 行数. */
    private Integer rowCount;
    /** 错误信息 (FAILED 时必填). */
    private String errorMsg;
    /** 来源系统标识. */
    private String sourceSystem;
    /** 上报时间. */
    private Instant reportedAt;
}
