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
     * @param plan 方案实体, 允许为 null (返回 null)
     * @return DTO 或 null
     */
    public static TargetPlanDTO toDto(PerfTargetPlan plan) {
        // Step 1 (RED) 骨架: 未实现, 强制 UT 失败
        throw new UnsupportedOperationException("TargetAssembler.toDto(plan) not implemented");
    }

    /**
     * 将目标值实体装配为对外 DTO.
     *
     * @param value 目标值实体, 允许为 null (返回 null)
     * @return DTO 或 null
     */
    public static TargetValueDTO toDto(PerfTargetValue value) {
        // Step 1 (RED) 骨架: 未实现, 强制 UT 失败
        throw new UnsupportedOperationException("TargetAssembler.toDto(value) not implemented");
    }
}
