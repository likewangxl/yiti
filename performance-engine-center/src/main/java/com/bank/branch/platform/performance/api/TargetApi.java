package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.TargetPlanDTO;
import com.bank.branch.platform.performance.api.dto.TargetValueDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 目标查询对外 API.
 *
 * <p>V1.0 全部实现 (配置 + 业务数据查询).
 *
 * <p>v1.2: planId 统一从 Long 改为 String, 对齐生产 DDL (perf_target_plan.id = varchar(32)).
 * 04 契约文档用 Long 是历史差错, 列为技术债待后续修订.
 */
public interface TargetApi {

    /**
     * 获取目标方案.
     */
    Optional<TargetPlanDTO> getTargetPlan(String planCode);

    /**
     * 按 ID 获取目标方案.
     * <p>v1.2: planId 为 String.
     */
    Optional<TargetPlanDTO> getTargetPlanById(String planId);

    /**
     * 获取特定主体在特定周期的目标值.
     *
     * @param planId      目标方案 ID (varchar 32)
     * @param subjectType EMP / ORG
     * @param subjectId   主体 ID
     * @param cycleKey    周期, 如 2026 / 2026Q2 / 202604
     * @param metricCode  指标编码
     * @return 目标值, 不存在时 empty
     */
    Optional<BigDecimal> getTargetValue(String planId, String subjectType, String subjectId, String cycleKey, String metricCode);

    /**
     * 批量查询某主体在某周期的所有目标值.
     */
    List<TargetValueDTO> listTargetValues(String planId, String subjectType, String subjectId, String cycleKey);
}
