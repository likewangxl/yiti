package com.bank.branch.platform.performance.service.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 目标值 upsert 命令 (单值).
 *
 * <p>批量 upsert 场景：Controller/Facade 直接传 {@code List<PerfTargetValue>} 给
 * {@code TargetValueService.upsertBatch}；单值 upsert 走本 Cmd + {@code upsertOne}
 * 方法，内部委托到 {@code upsertBatch(List.of(one), operator)}。
 *
 * <p>UK (plan_id, subject_type, subject_id, cycle_key, metric_code) 由 DB 保证，
 * Service 层不做 UK 冲突检查 (直接走 ON DUPLICATE KEY UPDATE).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertTargetValueCmd {

    /** 目标方案ID (必填). */
    private String planId;

    /** 对象类型 EMP/ORG (必填). */
    private String subjectType;

    /** 对象ID (必填). */
    private String subjectId;

    /** 周期键 例如 2026 / 2026Q1 (必填). */
    private String cycleKey;

    /** 指标编码 (必填). */
    private String metricCode;

    /** 目标值 (必填). */
    private BigDecimal targetValue;

    /** 基础值 (可空). */
    private BigDecimal baseValue;

    /**
     * 归属员工 ID（V1.4 S2.2 新增，可空）.
     *
     * <p>非空时写入 perf_target_value.owner_emp_id；为空时该行 owner_emp_id 为 NULL
     * （后续按业务批量回填，不走脚本兜底的 created_by 回填）.
     */
    private String ownerEmpId;

    /**
     * 归属机构编码（V1.4 S2.2 新增，可空）.
     *
     * <p>非空时写入 perf_target_value.owner_org_code；为空时该行保持 NULL（ORG scope 不命中）.
     */
    private String ownerOrgCode;

    /** 操作人 (必填, 覆写 createdBy). */
    private String operator;
}
