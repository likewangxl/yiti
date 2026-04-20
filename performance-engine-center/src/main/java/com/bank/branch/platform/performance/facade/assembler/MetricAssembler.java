package com.bank.branch.platform.performance.facade.assembler;

import com.bank.branch.platform.performance.api.dto.MetricDefDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;

/**
 * 指标 DTO 装配器.
 */
public final class MetricAssembler {

    private MetricAssembler() {
    }

    /**
     * 将实体转换为对外 DTO.
     *
     * @param entity 指标定义实体
     * @return 指标定义 DTO
     */
    public static MetricDefDTO toDto(PerfMetricDef entity) {
        if (entity == null) {
            return null;
        }
        return MetricDefDTO.builder()
                .metricCode(entity.getMetricCode())
                .metricName(entity.getMetricName())
                .metricNameEn(entity.getMetricNameEn())
                .description(entity.getMetricDesc())
                .baseDim(entity.getBaseDim())
                .metricLevel(entity.getMetricLevel())
                .calcFreq(entity.getCalcFreq())
                .calcMode(entity.getCalcMode())
                .calcLogicType(entity.getCalcLogicType())
                .valSlot(entity.getValSlot())
                .status(entity.getStatus())
                .refMetricCodes(entity.getRefMetricCodes())
                .build();
    }
}
