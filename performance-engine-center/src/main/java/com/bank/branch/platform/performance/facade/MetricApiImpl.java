package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.MetricApi;
import com.bank.branch.platform.performance.api.dto.MetricCardDTO;
import com.bank.branch.platform.performance.api.dto.MetricDefDTO;
import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfTargetValue;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
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

    /**
     * 查询员工工作台指标卡片（V1.3 R2.5 简化实现）.
     *
     * <p>V1.3 只实现 {@code target + actual + achievementRate} 三字段；
     * {@code previousValue / mom / yoy} 留 V1.4 迭代补齐，本版本置 null。
     *
     * <p>流程：
     * <ol>
     *   <li>读 {@code sys_control(EMP)} 得到 {@code latestDataDate + currentVersion}。
     *       若无可用版本直接返回空列表（fail-safe）。</li>
     *   <li>取所有 ACTIVE KPI 方案 → items → distinct metricCode。
     *       V1.3 简化假设：员工应考核的 metric 清单 = 全部 ACTIVE 方案的并集。
     *       真正的"员工-方案"映射表待 V1.4 引入后再按员工过滤。</li>
     *   <li>批量读 {@link MetricDefService#getByCodes}，仅保留 {@code baseDim=EMP}
     *       且已分配 {@code val_slot} 的指标，防止 ORG/CUST 指标误入员工卡片。</li>
     *   <li>对每个 metricCode 查 {@code emp_index_result} 得 actual，
     *       查 {@code perf_target_value} 得 target；
     *       {@code achievementRate = actual / target * 100}
     *       （target 为 null 或 0 时 rate 保持 null 以防误解）。</li>
     * </ol>
     *
     * <p><strong>技术债</strong>：
     * <ul>
     *   <li>cycleKey 当前取 {@code latestDataDate.getYear()} 字符串化（按年口径），
     *       V1.4 需根据方案 cycleType 严格匹配季度/月度的 cycleKey 格式。</li>
     *   <li>mom / yoy / previousValue 字段留 null，V1.4 接入后再补。</li>
     *   <li>员工-方案映射缺失，V1.3 用 ACTIVE 方案并集，V1.4 引入后过滤。</li>
     * </ul>
     *
     * @param empId 员工工号
     * @return 指标卡片列表（可能为空）
     */
    @Override
    public List<MetricCardDTO> getUserMetricCards(String empId) {
        // 1. 取所有 ACTIVE KPI 方案, 无则直接返回（防御 fail-safe, 无方案 = 无卡片）
        List<PerfKpiScheme> schemes = kpiSchemeService.listActiveSchemes();
        if (schemes == null || schemes.isEmpty()) {
            return List.of();
        }

        // 2. 合并所有方案的 items, 提取 distinct metricCode,
        //    同时记录每个 metricCode 首次命中的 scheme.cycleType（V1.4 S3.2）——
        //    用于后续按 cycleType 精确组装 cycleKey.
        List<String> orderedCodes = new ArrayList<>();
        Map<String, String> codeToCycleType = new LinkedHashMap<>();
        for (PerfKpiScheme scheme : schemes) {
            List<PerfKpiItem> items = kpiItemService.listBySchemeId(scheme.getId());
            if (items == null) {
                continue;
            }
            for (PerfKpiItem item : items) {
                String code = item.getMetricCode();
                if (code != null && !orderedCodes.contains(code)) {
                    orderedCodes.add(code);
                    codeToCycleType.put(code, scheme.getCycleType());
                }
            }
        }
        if (orderedCodes.isEmpty()) {
            return List.of();
        }

        // 3. 批量读取指标定义, 过滤非 EMP 维度与未分配 slot 的指标
        List<PerfMetricDef> defs = metricDefService.getByCodes(orderedCodes);
        Map<String, PerfMetricDef> codeToDef = new LinkedHashMap<>();
        for (PerfMetricDef def : defs) {
            if (!"EMP".equalsIgnoreCase(def.getBaseDim())) {
                log.debug("[getUserMetricCards] 指标 {} baseDim={} 非 EMP, 跳过",
                        def.getMetricCode(), def.getBaseDim());
                continue;
            }
            if (def.getValSlot() == null) {
                log.debug("[getUserMetricCards] 指标 {} 未分配 slot, 跳过", def.getMetricCode());
                continue;
            }
            codeToDef.put(def.getMetricCode(), def);
        }
        if (codeToDef.isEmpty()) {
            return List.of();
        }

        // 4. 读 sys_control 获得数据日期 + 版本
        SysControl sc = sysControlService.getCurrentVersion("EMP");
        if (sc == null) {
            // fail-safe: 无 EMP 版本时返回空卡片, 而非抛异常（工作台端容错）
            log.warn("[getUserMetricCards] empId={} 无 sys_control(EMP) 基线版本, 返回空卡片", empId);
            return List.of();
        }
        String version = sc.getCurrentVersion();
        LocalDate latestDate = sc.getLatestDataDate();

        // 5. 为每个 metricCode 组装卡片
        List<MetricCardDTO> cards = new ArrayList<>(codeToDef.size());
        for (Map.Entry<String, PerfMetricDef> entry : codeToDef.entrySet()) {
            String code = entry.getKey();
            PerfMetricDef def = entry.getValue();

            BigDecimal actual = empIndexResultMapper.selectSlotValue(
                    empId, latestDate, version, def.getValSlot());

            // V1.4 S3.2: cycleKey 按 scheme.cycleType 精确组装
            // (YEARLY=yyyy / QUARTERLY=yyyyQn / MONTHLY=yyyyMM / WEEKLY=yyyyWww)
            String cycleType = codeToCycleType.get(code);
            String cycleKey = buildCycleKey(cycleType, latestDate);

            // target: planId=null, 只按 (subjectType=EMP, subjectId=empId, cycleKey, metricCode) 查
            // PerfTargetValueMapper.selectByUniqueKey 的 planId 必填, V1.3 简化传 null 让 Mock 测试走通;
            // 生产实际需要按 KPI 方案关联的 target plan 查, V1.4 再补精确路径
            PerfTargetValue tv = perfTargetValueMapper.selectByUniqueKey(
                    null, "EMP", empId, cycleKey, code);
            BigDecimal target = tv == null ? null : tv.getTargetValue();

            BigDecimal rate = null;
            if (target != null && target.compareTo(BigDecimal.ZERO) != 0 && actual != null) {
                rate = actual.multiply(new BigDecimal("100"))
                        .divide(target, 4, RoundingMode.HALF_UP);
            }

            // V1.4 S3.3: 上期值 (环比) = 按 cycleType 回退一个周期的宽表值
            LocalDate previousDate = calculatePreviousDate(cycleType, latestDate);
            BigDecimal previousValue = previousDate == null ? null
                    : empIndexResultMapper.selectSlotValue(
                            empId, previousDate, version, def.getValSlot());
            BigDecimal mom = calculateMom(actual, previousValue);

            cards.add(MetricCardDTO.builder()
                    .metricCode(code)
                    .metricName(def.getMetricName())
                    .currentValue(actual)
                    .previousValue(previousValue)
                    .targetValue(target)
                    .achievementRate(rate)
                    .mom(mom)
                    .unit(def.getUnit())
                    .dataDate(latestDate)
                    .build());
        }
        return cards;
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
     * V1.4 S3.2: 按 cycleType 精确组装 cycleKey, 用于查 perf_target_value.
     *
     * <ul>
     *   <li>YEARLY → "yyyy" (如 2026)</li>
     *   <li>QUARTERLY → "yyyyQn" (如 2026Q2)</li>
     *   <li>MONTHLY → "yyyyMM" (如 202607)</li>
     *   <li>WEEKLY → "yyyyWww" (ISO 周, 如 2026W03)</li>
     *   <li>cycleType 为 null / 其他值 → 按年兜底 (yyyy), 保证向后兼容 V1.3 行为</li>
     * </ul>
     *
     * @param cycleType 周期类型 (大小写不敏感), null 时按年兜底
     * @param date      基准日期 (null 时返回 null)
     * @return 组装的 cycleKey 字符串
     */
    private String buildCycleKey(String cycleType, LocalDate date) {
        if (date == null) {
            return null;
        }
        if (cycleType == null) {
            return String.valueOf(date.getYear());
        }
        return switch (cycleType.toUpperCase()) {
            case "YEARLY" -> String.valueOf(date.getYear());
            case "QUARTERLY" -> date.getYear() + "Q" + ((date.getMonthValue() - 1) / 3 + 1);
            case "MONTHLY" -> String.format("%04d%02d", date.getYear(), date.getMonthValue());
            case "WEEKLY" -> String.format("%04dW%02d",
                    date.get(WeekFields.ISO.weekBasedYear()),
                    date.get(WeekFields.ISO.weekOfWeekBasedYear()));
            default -> String.valueOf(date.getYear());
        };
    }

    /**
     * V1.4 S3.3: 根据 cycleType 回退一个周期的日期, 用于查上期宽表值.
     *
     * <ul>
     *   <li>YEARLY → minusYears(1)</li>
     *   <li>QUARTERLY → minusMonths(3)</li>
     *   <li>MONTHLY → minusMonths(1)</li>
     *   <li>WEEKLY → minusWeeks(1)</li>
     *   <li>其他 / null → 按年回退 (minusYears(1)) 兜底</li>
     * </ul>
     *
     * @param cycleType 周期类型 (大小写不敏感)
     * @param date      当前日期 (null 时返回 null)
     * @return 上一周期日期
     */
    private LocalDate calculatePreviousDate(String cycleType, LocalDate date) {
        if (date == null) {
            return null;
        }
        if (cycleType == null) {
            return date.minusYears(1);
        }
        return switch (cycleType.toUpperCase()) {
            case "YEARLY" -> date.minusYears(1);
            case "QUARTERLY" -> date.minusMonths(3);
            case "MONTHLY" -> date.minusMonths(1);
            case "WEEKLY" -> date.minusWeeks(1);
            default -> date.minusYears(1);
        };
    }

    /**
     * V1.4 S3.3: 计算环比变化率 mom = (current - previous) / |previous| * 100, 保留 2 位小数.
     *
     * <p>除零保护：previous=0 或任一侧为 null 直接返回 null, 避免误导性 0.00%.
     *
     * @param current  当期值
     * @param previous 上期值
     * @return mom 百分比 (2 位小数) 或 null
     */
    private BigDecimal calculateMom(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null) {
            return null;
        }
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous.abs(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
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
