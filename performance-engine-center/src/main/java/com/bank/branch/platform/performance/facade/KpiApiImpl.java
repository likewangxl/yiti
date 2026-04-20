package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.KpiApi;
import com.bank.branch.platform.performance.api.dto.KpiResultDTO;
import com.bank.branch.platform.performance.api.dto.KpiSchemeDTO;
import com.bank.branch.platform.performance.service.KpiItemService;
import com.bank.branch.platform.performance.service.KpiSchemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * KPI 查询对外 API 实现 (V1.0 骨架, 尚未实现).
 */
@Service
@RequiredArgsConstructor
public class KpiApiImpl implements KpiApi {

    private final KpiSchemeService kpiSchemeService;
    private final KpiItemService kpiItemService;

    @Override
    public BigDecimal getCurrentKpiTotal(String empId, String cycleType) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<KpiResultDTO> getCurrentKpiResult(String empId, String cycleType) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<KpiResultDTO> getKpiHistory(String empId, String cycleType, LocalDate from, LocalDate to) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<KpiSchemeDTO> getKpiScheme(String schemeCode) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<KpiSchemeDTO> getKpiSchemeById(String schemeId) {
        throw new UnsupportedOperationException("not implemented");
    }
}
