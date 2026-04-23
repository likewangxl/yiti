package com.bank.branch.platform.performance.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 目标修正申请 perf_target_adjust_apply 贫血实体 (V1.2 Q3.1).
 *
 * <p>字段严格对齐生产 DDL (docs/schema/ddl-performance.sql §17)：
 * <ul>
 *   <li>{@code id} varchar(32) 主键（申请 ID）</li>
 *   <li>{@code plan_id} varchar(32) 目标方案 ID（非 target_plan_id）</li>
 *   <li>{@code subject_type} varchar(20) 对象类型：EMP / ORG（双维度）</li>
 *   <li>{@code subject_id} varchar(50) 对象 ID（员工号或机构编码）</li>
 *   <li>{@code cycle_key} varchar(20) 周期键（如 "2026Q1" / "202604" / "2026"）</li>
 *   <li>{@code status} 状态：DRAFT / IN_APPROVAL / APPROVED / REJECTED（默认 DRAFT）</li>
 *   <li>{@code business_key} 流程业务键（格式 TARGET_ADJUST:{id}）</li>
 *   <li>{@code process_instance_id} Flowable 流程实例 ID</li>
 *   <li>{@code owner_org_id} 归属机构（数据范围过滤基准）</li>
 *   <li>{@code remark} 备注（text，承载目标值修改建议的结构化 JSON）</li>
 *   <li>审计：created_by / created_time / updated_by / updated_time</li>
 * </ul>
 *
 * <p>V1.2 方案：目标值实际修改建议通过 remark JSON 记录，结构示例：
 * <pre>
 * {
 *   "adjustments": [
 *     {"metricCode": "M_DEP_BAL", "oldValue": 100, "newValue": 120},
 *     {"metricCode": "M_FEE_INCOME", "oldValue": 50, "newValue": 60}
 *   ],
 *   "reason": "2026 Q1 目标上调 20%"
 * }
 * </pre>
 * 审批通过后，{@code TargetAdjustCompletedListener} 解析 remark 并调用
 * {@code PerfTargetValueMapper.upsertBatch} 把 (plan_id, subject_type, subject_id,
 * metric_code, cycle_key) 五元组对应的目标值更新为 newValue.
 *
 * <p>生产 DDL **不存在**字段：{@code metric_code / old_target_value / new_target_value /
 * applicant / apply_time / approve_time}，目标值明细以 remark JSON 形式承载.
 *
 * @since V1.2 Q3.1
 */
@Data
public class PerfTargetAdjustApply {

    /** 申请 ID（varchar(32) 主键）. */
    private String id;

    /** 目标方案 ID（varchar(32)，对齐 V1.0 planId 使用 String 的技术债决策）. */
    private String planId;

    /** 对象类型：EMP / ORG. */
    private String subjectType;

    /** 对象 ID（员工号或机构编码）. */
    private String subjectId;

    /** 周期键（如 "2026Q1" / "202604" / "2026"）. */
    private String cycleKey;

    /** 状态：DRAFT / IN_APPROVAL / APPROVED / REJECTED. */
    private String status;

    /** 流程业务键（格式 TARGET_ADJUST:{id}）. */
    private String businessKey;

    /** Flowable 流程实例 ID. */
    private String processInstanceId;

    /** 归属机构（数据范围过滤基准）. */
    private String ownerOrgId;

    /** 备注（text，承载目标值修改建议的 JSON 快照）. */
    private String remark;

    /** 创建人（申请人 empId）. */
    private String createdBy;

    /** 创建时间（DB 默认 CURRENT_TIMESTAMP）. */
    private LocalDateTime createdTime;

    /** 更新人. */
    private String updatedBy;

    /** 更新时间（DB ON UPDATE CURRENT_TIMESTAMP）. */
    private LocalDateTime updatedTime;
}
