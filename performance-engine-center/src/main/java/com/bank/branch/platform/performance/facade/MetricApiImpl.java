package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.MetricApi;
import com.bank.branch.platform.performance.api.dto.MetricCardDTO;
import com.bank.branch.platform.performance.api.dto.MetricDefDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.facade.assembler.MetricAssembler;
import com.bank.branch.platform.performance.service.MetricDefService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 指标查询对外 API 实现.
 */
@Service
@RequiredArgsConstructor
public class MetricApiImpl implements MetricApi {

    private final MetricDefService metricDefService;

    @Override
    public List<MetricCardDTO> getUserMetricCards(String empId) {
        throw new UnsupportedOperationException("V1.1 delivered");
    }

    @Override
    @Cacheable(cacheNames = "perf:metric_def", key = "#metricCode")
    public Optional<MetricDefDTO> getMetricDef(String metricCode) {
        return Optional.ofNullable(metricDefService.getByCodeOrNull(metricCode))
                .map(MetricAssembler::toDto);
    }

    @Override
    public List<MetricDefDTO> getMetricDefs(List<String> metricCodes) {
        return metricDefService.getByCodes(metricCodes).stream()
                .map(MetricAssembler::toDto)
                .toList();
    }

    @Override
    @Cacheable(cacheNames = "perf:metric_def:list", key = "#baseDim + ':' + (#metricLevel == null ? 'ALL' : #metricLevel)")
    public List<MetricDefDTO> listMetrics(String baseDim, Integer metricLevel) {
        return metricDefService.listActiveMetrics(baseDim, metricLevel).stream()
                .map(MetricAssembler::toDto)
                .toList();
    }

    @Override
    public Map<String, BigDecimal> getEmpMetricValues(String empId, LocalDate dataDate, List<String> metricCodes) {
        throw new UnsupportedOperationException("V1.1 delivered");
    }

    @Override
    public Map<String, BigDecimal> getOrgMetricValues(String orgCode, LocalDate dataDate, List<String> metricCodes) {
        throw new UnsupportedOperationException("V1.1 delivered");
    }

    @Override
    public Map<String, BigDecimal> getCustMetricValues(String custId, LocalDate dataDate, List<String> metricCodes) {
        throw new UnsupportedOperationException("V1.1 delivered");
    }
}
