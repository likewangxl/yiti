package com.bank.branch.platform.performance.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 指标立即执行请求 DTO（03 §A.6）.
 *
 * <p>端点：{@code POST /api/perf/metrics/{metricCode}/execute}
 *
 * <p>字段：
 * <ul>
 *   <li>{@code dataDate}：必填，执行的数据日期</li>
 *   <li>{@code cascade}：可选，是否级联刷新下游，默认 true</li>
 *   <li>{@code async}：可选，是否异步执行，默认 true</li>
 *   <li>{@code reason}：必填（高危操作，审计要求）</li>
 * </ul>
 */
@Data
public class MetricExecuteReqDTO {

    /** 数据日期（必填）. */
    @NotNull(message = "dataDate 不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataDate;

    /** 是否级联刷新下游，默认 true. */
    private Boolean cascade = Boolean.TRUE;

    /** 是否异步执行，默认 true. */
    private Boolean async = Boolean.TRUE;

    /** 执行原因（必填，对应 @AuditLog reasonRequired=true）. */
    @NotBlank(message = "reason 不能为空")
    @Size(max = 500, message = "reason 不能超过 500 字")
    private String reason;
}
