package com.bank.branch.platform.performance.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 指标批量执行请求（POST /api/perf/metrics/batch-execute）. */
@Data
public class BatchExecuteReqDTO {

    /** 指标编码列表（必填、非空；同步执行软上限 50 防超时）. */
    @NotEmpty(message = "metricCodes 不能为空")
    @Size(max = 50, message = "单次批量执行指标数不能超过 50")
    private List<String> metricCodes;

    /** 数据日期（必填，= 要计算哪天的数据）. */
    @NotNull(message = "dataDate 不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataDate;

    /** 执行原因（高危操作必填，@AuditLog reasonRequired=true）. */
    @NotBlank(message = "reason 不能为空")
    @Size(max = 500, message = "reason 不能超过 500 字")
    private String reason;
}
