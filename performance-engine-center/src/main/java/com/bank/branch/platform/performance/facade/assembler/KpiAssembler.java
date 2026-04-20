package com.bank.branch.platform.performance.facade.assembler;

import com.bank.branch.platform.performance.api.dto.KpiItemDTO;
import com.bank.branch.platform.performance.api.dto.KpiSchemeDTO;
import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;

import java.util.List;

/**
 * KPI 方案 + 方案项 DTO 装配器.
 *
 * <p>职责仅做字段映射, 不查 DB / 不调 Service, 以便 Facade 可单元测试隔离。
 */
public final class KpiAssembler {

    private KpiAssembler() {
    }

    /**
     * 将方案实体 + 方案项列表装配成对外 DTO.
     *
     * @param scheme 方案实体
     * @param items  方案项列表 (可能为空, null 视同空)
     * @return 方案 DTO (scheme 为 null 时返回 null)
     */
    public static KpiSchemeDTO toDto(PerfKpiScheme scheme, List<PerfKpiItem> items) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 将方案项实体转换为 DTO.
     *
     * @param item 方案项实体
     * @return 方案项 DTO
     */
    public static KpiItemDTO toItemDto(PerfKpiItem item) {
        throw new UnsupportedOperationException("not implemented");
    }
}
