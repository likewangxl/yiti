package com.bank.branch.platform.performance.facade.assembler;

import com.bank.branch.platform.performance.api.dto.TargetPlanDTO;
import com.bank.branch.platform.performance.api.dto.TargetValueDTO;
import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.entity.PerfTargetValue;

/**
 * 目标方案 / 目标值 DTO 装配器.
 *
 * <p>职责仅做字段映射, 不查 DB / 不调 Service, 以便 Facade 可单元测试隔离。
 *
 * <p>v1.2: id / planId / kpiSchemeId 全部 String (对齐生产 DDL varchar(32)),
 * 04 契约文档原用 Long 是历史差错, 列为技术债待架构师后续统一处理。
 */
public final class TargetAssembler {

    private TargetAssembler() {
    }

    /**
     * 将目标方案实体装配为对外 DTO.
     *
     * <p>仅映射对外字段 (id/planCode/planName/kpiSchemeId/targetDim/targetCycle/effectiveDate/status);
     * 审计字段 createdBy/createdTime/updatedBy/updatedTime 不对外暴露。
     *
     * @param plan 方案实体, 允许为 null (返回 null)
     * @return DTO 或 null
     */
    public static TargetPlanDTO toDto(PerfTargetPlan plan) {
        if (plan == null) {
            return null;
        }
        return TargetPlanDTO.builder()
                .id(plan.getId())
                .planCode(plan.getPlanCode())
                .planName(plan.getPlanName())
                .kpiSchemeId(plan.getKpiSchemeId())
                .targetDim(plan.getTargetDim())
                .targetCycle(plan.getTargetCycle())
                .effectiveDate(plan.getEffectiveDate())
                .status(plan.getStatus())
                .build();
    }

    /**
     * 将目标值实体装配为对外 DTO.
     *
     * <p>仅映射对外字段 (id/planId/subjectType/subjectId/cycleKey/metricCode/targetValue/baseValue);
     * 审计字段不对外暴露。
     *
     * @param value 目标值实体, 允许为 null (返回 null)
     * @return DTO 或 null
     */
    public static TargetValueDTO toDto(PerfTargetValue value) {
        if (value == null) {
            return null;
        }
        return TargetValueDTO.builder()
                .id(value.getId())
                .planId(value.getPlanId())
                .subjectType(value.getSubjectType())
                .subjectId(value.getSubjectId())
                .cycleKey(value.getCycleKey())
                .metricCode(value.getMetricCode())
                .targetValue(value.getTargetValue())
                .baseValue(value.getBaseValue())
                .build();
    }
}
