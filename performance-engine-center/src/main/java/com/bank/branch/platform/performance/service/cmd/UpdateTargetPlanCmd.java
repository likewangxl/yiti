package com.bank.branch.platform.performance.service.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 更新目标方案命令.
 *
 * <p>选择性 patch (非空字段才更新, 对齐 Mapper.updateByIdSelective 语义).
 * 方案编码 (plan_code) 为 UK, **不允许**修改; kpiSchemeId 也不建议修改, 若需切换
 * 应通过"禁用旧方案 + 新建方案"组合操作以保持审计清晰。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTargetPlanCmd {

    /** 方案名称 (可空). */
    private String planName;

    /** 关联 KPI 方案 ID (可空, null 表示不修改). */
    private String kpiSchemeId;

    /** 方案状态 (可空, ACTIVE/DISABLED). */
    private String status;

    /** 目标维度 (可空). */
    private String targetDim;

    /** 目标周期 (可空). */
    private String targetCycle;

    /** 生效日期 (可空). */
    private LocalDate effectiveDate;

    /** 方案覆盖起始日期 (可空，2026-05-22 新增). */
    private LocalDate startDate;

    /** 方案覆盖截止日期 (可空，2026-05-22 新增). */
    private LocalDate endDate;

    /** 操作人. */
    private String operator;
}
