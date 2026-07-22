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

    /** 指标编码列表（必填、非空；软上限 50，防单次提交过多压垮执行线程池）. */
    @NotEmpty(message = "metricCodes 不能为空")
    @Size(max = 50, message = "单次批量执行指标数不能超过 50")
    private List<String> metricCodes;

    /**
     * 是否异步提交，默认 true.
     *
     * <p>true：逐个预建任务行后交后台线程池，接口立即返回，各项 status=PENDING，
     * 响应里的 success/failed 是<b>提交</b>成功/失败数，真实结果需轮询任务历史；
     * false：逐个同步执行到终态才返回（大批量易撞网关读超时，仅供脚本/排障使用）。
     */
    private Boolean async = Boolean.TRUE;

    /** 数据日期（必填，= 要计算哪天的数据）. */
    @NotNull(message = "dataDate 不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataDate;

    /** 执行原因（高危操作必填，@AuditLog reasonRequired=true）. */
    @NotBlank(message = "reason 不能为空")
    @Size(max = 500, message = "reason 不能超过 500 字")
    private String reason;
}
