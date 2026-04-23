package com.bank.branch.platform.performance.service.adjust.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 目标修正申请提交命令 (V1.2 Q3.2a).
 *
 * <p>Service 层 {@code TargetAdjustService.submit(cmd)} 消费：
 * <ol>
 *   <li>校验字段：planId / subjectType(EMP/ORG) / subjectId / cycleKey / ownerOrgId /
 *       reason / adjustments 必填；adjustments 的 metricCode 去重，newValue 非空</li>
 *   <li>校验 planId 对应 {@code perf_target_plan} 存在（TARGET_PLAN_NOT_FOUND）</li>
 *   <li>生成 applyNo 语义标识（内部 UUID + 前缀 TA + yyyyMMdd）</li>
 *   <li>将 adjustments + reason 序列化为 JSON 写入 remark 字段</li>
 *   <li>插入主表 status=IN_APPROVAL</li>
 *   <li>启动 {@code perf_target_adjust_v1} BPMN（不分对公/零售）</li>
 *   <li>回写 processInstanceId</li>
 * </ol>
 *
 * <p>remark JSON 结构示例：
 * <pre>
 * {
 *   "adjustments": [
 *     {"metricCode": "M_DEP_BAL", "oldValue": 100, "newValue": 120},
 *     {"metricCode": "M_FEE_INCOME", "oldValue": 50, "newValue": 60}
 *   ],
 *   "reason": "2026 Q1 目标上调 20%"
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitTargetAdjustCmd {

    /** 目标方案 ID（必填）. */
    private String planId;

    /** 对象类型：EMP / ORG（必填）. */
    private String subjectType;

    /** 对象 ID（员工号或机构编码，必填）. */
    private String subjectId;

    /** 周期键（如 "2026Q1" / "202604" / "2026"，必填）. */
    private String cycleKey;

    /** 归属机构（必填，数据范围过滤基准）. */
    private String ownerOrgId;

    /** 申请原因（高危必填，与 AuditLog reasonRequired 协同）. */
    private String reason;

    /** 申请人工号（即 created_by，必填）. */
    private String applicant;

    /** 调整明细（非空，metricCode 去重）. */
    private List<TargetAdjustment> adjustments;

    /**
     * 调整明细项.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TargetAdjustment {

        /** 指标编码（必填，去重）. */
        private String metricCode;

        /** 原目标值（可空，仅用于审计快照）. */
        private BigDecimal oldValue;

        /** 新目标值（必填，审批通过后落地到 perf_target_value）. */
        private BigDecimal newValue;
    }
}
