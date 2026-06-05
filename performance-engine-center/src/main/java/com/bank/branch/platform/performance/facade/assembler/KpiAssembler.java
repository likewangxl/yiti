package com.bank.branch.platform.performance.facade.assembler;

import com.bank.branch.platform.performance.api.dto.KpiItemDTO;
import com.bank.branch.platform.performance.api.dto.KpiSchemeDTO;
import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;

import java.util.Collections;
import java.util.List;

/**
 * KPI 方案 + 方案项 DTO 装配器.
 *
 * <p>职责仅做字段映射, 不查 DB / 不调 Service, 以便 Facade 可单元测试隔离。
 * <p>**字段缺口说明**: V1.0 的 {@link PerfKpiItem} 实体不含 {@code metricName} / {@code sortNo}
 * 字段 (DDL 未建列), 对应 DTO 字段留 {@code null}; V1.1 若需要填充 metricName,
 * 由 Facade 层按需额外查 {@code perf_metric_def} 并在 assembler 外部补齐。
 */
public final class KpiAssembler {

    private KpiAssembler() {
    }

    /**
     * 将方案实体 + 方案项列表装配成对外 DTO.
     *
     * <p>{@code scheme.openDetail} 以整数存储 (0/1), DTO 转为 {@link Boolean} 布尔值;
     * {@code null} 被视为 {@code false}。
     *
     * @param scheme 方案实体
     * @param items  方案项列表 (null 视同空)
     * @return 方案 DTO (scheme 为 null 时返回 null)
     */
    public static KpiSchemeDTO toDto(PerfKpiScheme scheme, List<PerfKpiItem> items) {
        if (scheme == null) {
            return null;
        }
        List<PerfKpiItem> safeItems = items == null ? Collections.emptyList() : items;
        List<KpiItemDTO> itemDtos = safeItems.stream()
                .map(KpiAssembler::toItemDto)
                .toList();
        return KpiSchemeDTO.builder()
                .id(scheme.getId())
                .schemeCode(scheme.getSchemeCode())
                .schemeName(scheme.getSchemeName())
                .cycleType(scheme.getCycleType())
                .openDetail(scheme.getOpenDetail() != null && scheme.getOpenDetail() == 1)
                .status(scheme.getStatus())
                .items(itemDtos)
                .build();
    }

    /**
     * 将方案项实体转换为 DTO (metricName / sortNo 当前无对应列, 留 null).
     *
     * @param item 方案项实体 (非 null)
     * @return 方案项 DTO
     */
    public static KpiItemDTO toItemDto(PerfKpiItem item) {
        return KpiItemDTO.builder()
                .id(item.getId())
                .metricCode(item.getMetricCode())
                .metricName(null)
                .weight(item.getWeight())
                .multiplier(item.getMultiplier())
                .minScore(item.getMinScore())
                .maxScore(item.getMaxScore())
                .formula(item.getFormula())
                .sortNo(null)
                .build();
    }
}
