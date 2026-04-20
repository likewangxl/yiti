package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.KpiApi;
import com.bank.branch.platform.performance.api.dto.KpiResultDTO;
import com.bank.branch.platform.performance.api.dto.KpiSchemeDTO;
import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.facade.assembler.KpiAssembler;
import com.bank.branch.platform.performance.service.KpiItemService;
import com.bank.branch.platform.performance.service.KpiSchemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * KPI 查询对外 API 实现.
 *
 * <p>V1.0 契约 (spec §5.2.3):
 * <ul>
 *   <li>{@link #getKpiScheme(String)} / {@link #getKpiSchemeById(String)} 返回方案 + 方案项组装 DTO</li>
 *   <li>{@link #getCurrentKpiTotal} / {@link #getCurrentKpiResult} / {@link #getKpiHistory}
 *       抛 {@link UnsupportedOperationException} ("V1.1 delivered")</li>
 * </ul>
 *
 * <p>缓存策略:
 * <ul>
 *   <li>{@link #getKpiSchemeById} 使用 {@code perf:kpi_scheme} 缓存, key 为方案 ID</li>
 *   <li>{@link #getKpiScheme}(code) 不缓存: 按 code 查本质是 code → scheme 的二级查询,
 *       加缓存会让写方法的 evict 难以定位 (不知道 code), 故 V1.0 直接穿透到 Service。
 *       写方法 evict 仅针对 id 缓存 ({@code KpiSchemeService.updateById/publish/disable})</li>
 * </ul>
 *
 * <p>消费方: portal-content-center, report-analytics-center.
 */
@Service
@RequiredArgsConstructor
public class KpiApiImpl implements KpiApi {

    private final KpiSchemeService kpiSchemeService;
    private final KpiItemService kpiItemService;

    @Override
    public BigDecimal getCurrentKpiTotal(String empId, String cycleType) {
        throw new UnsupportedOperationException("V1.1 delivered");
    }

    @Override
    public Optional<KpiResultDTO> getCurrentKpiResult(String empId, String cycleType) {
        throw new UnsupportedOperationException("V1.1 delivered");
    }

    @Override
    public List<KpiResultDTO> getKpiHistory(String empId, String cycleType, LocalDate from, LocalDate to) {
        throw new UnsupportedOperationException("V1.1 delivered");
    }

    @Override
    public Optional<KpiSchemeDTO> getKpiScheme(String schemeCode) {
        return kpiSchemeService.getBySchemeCodeOrNull(schemeCode)
                .map(this::assembleWithItems);
    }

    @Override
    @Cacheable(cacheNames = "perf:kpi_scheme", key = "#schemeId")
    public Optional<KpiSchemeDTO> getKpiSchemeById(String schemeId) {
        return kpiSchemeService.getByIdOrNull(schemeId)
                .map(this::assembleWithItems);
    }

    /**
     * 组装方案 DTO (含方案项): 按 id 查方案项后委托 {@link KpiAssembler} 做字段映射.
     *
     * @param scheme 已查到的方案实体
     * @return 含 items 的方案 DTO
     */
    private KpiSchemeDTO assembleWithItems(PerfKpiScheme scheme) {
        List<PerfKpiItem> items = kpiItemService.listBySchemeId(scheme.getId());
        return KpiAssembler.toDto(scheme, items == null ? Collections.emptyList() : items);
    }
}
