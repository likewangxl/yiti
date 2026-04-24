package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.MetricApi;
import com.bank.branch.platform.performance.api.dto.MetricCardDTO;
import com.bank.branch.platform.performance.api.dto.MetricDefDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.facade.assembler.MetricAssembler;
import com.bank.branch.platform.performance.mapper.CustIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetValueMapper;
import com.bank.branch.platform.performance.service.KpiItemService;
import com.bank.branch.platform.performance.service.KpiSchemeService;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.service.SysControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 指标查询对外 API 实现（V1.1 Task P2.6）.
 *
 * <p>V1.1 P2.6 交付：将原 3 个 UOE 占位的宽表查询 {@code getEmpMetricValues / getOrgMetricValues /
 * getCustMetricValues} 替换为真实实现——
 * 批量读取指标定义的 {@code val_slot} → 按 base_dim 路由对应宽表 → 返回
 * {@code (metricCode -> metricValue)} 映射。
 *
 * <p>V1.1 P2 不实现 {@link #getUserMetricCards(String)}（V1.2 目标/实绩联动）.
 *
 * <p><strong>批量上限</strong>：metricCodes.size() &gt; 100 抛
 * {@link PerfErrorCode#BATCH_QUERY_EXCEEDS_LIMIT}（PERF-42206）.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricApiImpl implements MetricApi {

    /** 批量查询上限（来自 §K MetricApi 批量约束）. */
    private static final int MAX_BATCH_METRIC_CODES = 100;

    private final MetricDefService metricDefService;
    private final SysControlService sysControlService;
    private final EmpIndexResultMapper empIndexResultMapper;
    private final OrgIndexResultMapper orgIndexResultMapper;
    private final CustIndexResultMapper custIndexResultMapper;
    /** V1.3 R2.5 新增: 取员工 KPI 方案列表以组装卡片的指标清单. */
    private final KpiSchemeService kpiSchemeService;
    /** V1.3 R2.5 新增: 读 KPI 方案项. */
    private final KpiItemService kpiItemService;
    /** V1.3 R2.5 新增: 读员工目标值（subjectType=EMP）. */
    private final PerfTargetValueMapper perfTargetValueMapper;

    @Override
    public List<MetricCardDTO> getUserMetricCards(String empId) {
        // V1.3 R2.5 Red 占位: Green 阶段替换为 KPI 方案 + 目标/实绩联动
        throw new UnsupportedOperationException("V1.3 R2.5 Red placeholder");
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
    @Cacheable(cacheNames = "perf:metric_def:list",
            key = "#baseDim + ':' + (#metricLevel == null ? 'ALL' : #metricLevel)")
    public List<MetricDefDTO> listMetrics(String baseDim, Integer metricLevel) {
        return metricDefService.listActiveMetrics(baseDim, metricLevel).stream()
                .map(MetricAssembler::toDto)
                .toList();
    }

    @Override
    public Map<String, BigDecimal> getEmpMetricValues(String empId, LocalDate dataDate,
                                                     List<String> metricCodes) {
        return queryValues("EMP", empId, dataDate, metricCodes);
    }

    @Override
    public Map<String, BigDecimal> getOrgMetricValues(String orgCode, LocalDate dataDate,
                                                     List<String> metricCodes) {
        return queryValues("ORG", orgCode, dataDate, metricCodes);
    }

    @Override
    public Map<String, BigDecimal> getCustMetricValues(String custId, LocalDate dataDate,
                                                      List<String> metricCodes) {
        return queryValues("CUST", custId, dataDate, metricCodes);
    }

    /**
     * 按 baseDim 统一路由宽表查询.
     *
     * @param baseDim     维度 EMP/ORG/CUST
     * @param baseKey     维度键（empId / orgCode / custId）
     * @param dataDate    数据日期（null 则取 sys_control.latest_data_date）
     * @param metricCodes 指标编码列表，最多 100
     * @return (metricCode -> value) 映射；未命中或未分配 slot 的 code 不出现在 Map 中
     * @throws PerfException metricCodes 超 100 → BATCH_QUERY_EXCEEDS_LIMIT
     */
    private Map<String, BigDecimal> queryValues(String baseDim, String baseKey,
                                                LocalDate dataDate, List<String> metricCodes) {
        // 1. 入参校验
        if (metricCodes == null || metricCodes.isEmpty()) {
            return new LinkedHashMap<>();
        }
        if (metricCodes.size() > MAX_BATCH_METRIC_CODES) {
            throw new PerfException(PerfErrorCode.BATCH_QUERY_EXCEEDS_LIMIT,
                    "metricCodes 数量=" + metricCodes.size());
        }

        // 2. 读取指标定义
        List<PerfMetricDef> defs = metricDefService.getByCodes(metricCodes);
        if (defs == null || defs.isEmpty()) {
            return new LinkedHashMap<>();
        }

        // 3. 分辨日期与版本
        LocalDate effectiveDate = dataDate;
        String version;
        SysControl sysControl = sysControlService.getCurrentVersion(baseDim);
        version = sysControl.getCurrentVersion();
        if (effectiveDate == null) {
            effectiveDate = sysControl.getLatestDataDate();
        }

        // 4. 逐 code 按维度匹配查宽表
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (PerfMetricDef def : defs) {
            // 仅同维度的指标参与查询（防止 ORG 指标错用 EMP 宽表）
            if (!baseDim.equalsIgnoreCase(def.getBaseDim())) {
                log.debug("[MetricApi] 指标 {} baseDim={} 与请求维度 {} 不符，跳过",
                        def.getMetricCode(), def.getBaseDim(), baseDim);
                continue;
            }
            Integer slot = def.getValSlot();
            if (slot == null) {
                log.debug("[MetricApi] 指标 {} 未分配 val_slot，跳过", def.getMetricCode());
                continue;
            }
            BigDecimal value;
            switch (baseDim) {
                case "EMP":
                    value = empIndexResultMapper.selectSlotValue(baseKey, effectiveDate, version, slot);
                    break;
                case "ORG":
                    value = orgIndexResultMapper.selectSlotValue(baseKey, effectiveDate, version, slot);
                    break;
                case "CUST":
                    value = custIndexResultMapper.selectSlotValue(baseKey, effectiveDate, version, slot);
                    break;
                default:
                    throw new PerfException(PerfErrorCode.BIZ_KIND_INVALID, "未知 baseDim=" + baseDim);
            }
            if (value != null) {
                result.put(def.getMetricCode(), value);
            }
        }
        return result;
    }
}
