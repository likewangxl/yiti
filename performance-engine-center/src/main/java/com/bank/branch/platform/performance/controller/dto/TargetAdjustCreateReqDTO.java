package com.bank.branch.platform.performance.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 目标修正申请创建请求 DTO (V1.2 Q3.2c).
 *
 * <p>对应端点：{@code POST /api/perf/target-adjust/create}
 *
 * <p>按生产 DDL §17 承载：planId/subjectType/subjectId/cycleKey + ownerOrgId +
 * reason（必填，存 remark JSON）+ adjustments（metricCode/oldValue/newValue，
 * 最终序列化进 remark JSON）.
 */
@Data
public class TargetAdjustCreateReqDTO {

    /** 目标方案 ID（必填）. */
    @NotBlank(message = "planId 必填")
    private String planId;

    /** 对象类型：EMP / ORG（必填）. */
    @NotBlank(message = "subjectType 必填")
    private String subjectType;

    /** 对象 ID（员工号或机构编码，必填）. */
    @NotBlank(message = "subjectId 必填")
    private String subjectId;

    /** 周期键（必填，如 "2026Q1"）. */
    @NotBlank(message = "cycleKey 必填")
    private String cycleKey;

    /** 归属机构（必填，数据范围基准）. */
    @NotBlank(message = "ownerOrgId 必填")
    private String ownerOrgId;

    /** 申请原因（高危必填，审计留痕）. */
    @NotBlank(message = "reason 必填")
    @Size(max = 500, message = "reason 长度不超过 500")
    private String reason;

    /** 调整明细（必填，metricCode 去重）. */
    @NotNull(message = "adjustments 必填")
    @Valid
    private List<Adjustment> adjustments;

    /**
     * 调整明细项.
     */
    @Data
    public static class Adjustment {

        /** 指标编码. */
        @NotBlank(message = "adjustment.metricCode 必填")
        private String metricCode;

        /** 原目标值（可空，审计快照）. */
        private BigDecimal oldValue;

        /** 新目标值（必填，审批通过后落地）. */
        @NotNull(message = "adjustment.newValue 必填")
        private BigDecimal newValue;
    }
}
