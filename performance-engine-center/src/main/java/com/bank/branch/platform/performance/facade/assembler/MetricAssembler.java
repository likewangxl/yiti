package com.bank.branch.platform.performance.facade.assembler;

import com.bank.branch.platform.performance.api.dto.MetricDefDTO;
import com.bank.branch.platform.performance.controller.dto.MetricDefRespDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;

/**
 * 指标 DTO 装配器.
 */
public final class MetricAssembler {

    private MetricAssembler() {
    }

    /**
     * 将实体转换为对外 DTO（供跨模块调用的 Api 层使用）.
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
                .description(entity.getDescription())
                .metricDesc(entity.getMetricDesc())
                .baseDim(entity.getBaseDim())
                .metricLevel(entity.getMetricLevel())
                .calcFreq(entity.getCalcFreq())
                .calcMode(entity.getCalcMode())
                .calcLogicType(entity.getCalcLogicType())
                .valSlot(entity.getValSlot())
                .status(entity.getStatus())
                .refMetricCodes(entity.getRefMetricCodes())
                .unit(entity.getUnit())
                .decimalPlaces(entity.getDecimalPlaces())
                .metricCategory(entity.getMetricCategory())
                .build();
    }

    /**
     * 将实体转换为 Controller 层响应 DTO（隐藏 entity 内部字段）.
     *
     * <p>不包含 deleted / createdTime / updatedTime / createdBy / updatedBy 等内部字段，
     * 防止 entity 结构泄漏到 API 响应层。
     *
     * @param entity 指标定义实体
     * @return Controller 层响应 DTO
     */
    public static MetricDefRespDTO toRespDTO(PerfMetricDef entity) {
        if (entity == null) {
            return null;
        }
        MetricDefRespDTO dto = new MetricDefRespDTO();
        dto.setId(entity.getId());
        dto.setMetricCode(entity.getMetricCode());
        dto.setMetricName(entity.getMetricName());
        dto.setMetricNameEn(entity.getMetricNameEn());
        dto.setMetricDesc(entity.getMetricDesc());
        dto.setBaseDim(entity.getBaseDim());
        dto.setMetricLevel(entity.getMetricLevel());
        dto.setCalcFreq(entity.getCalcFreq());
        dto.setCalcMode(entity.getCalcMode());
        dto.setCalcLogicType(entity.getCalcLogicType());
        dto.setSqlText(entity.getSqlText());
        dto.setExprText(entity.getExprText());
        // exprDisplay 不再从实体读取（列已废弃）；详情路径 getByCodeDto 按 exprText 实时派生后回填
        dto.setSummaryRule(entity.getSummaryRule());
        dto.setRefMetricCodes(entity.getRefMetricCodes());
        dto.setValSlot(entity.getValSlot());
        dto.setStatus(entity.getStatus());
        dto.setUnit(entity.getUnit());
        dto.setDecimalPlaces(entity.getDecimalPlaces());
        dto.setDescription(entity.getDescription());
        dto.setMetricCategory(entity.getMetricCategory());
        // 审计字段：创建人/更新人原始 empId + 创建/更新时间（username/中文名由 Service 详情路径解析填充）
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setCreatedTime(entity.getCreatedTime());
        dto.setUpdatedTime(entity.getUpdatedTime());
        return dto;
    }
}
