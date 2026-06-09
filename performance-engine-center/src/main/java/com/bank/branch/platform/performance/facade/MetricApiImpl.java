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
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 指标查询对外 API 实现（V1.5 P3.1: 按 (metricCode, cycleType) 组合去重，修复 V1.4 M01 首命中歧义）.
 *
 * <p>V1.1 P2.6 交付：将原 3 个 UOE 占位的宽表查询 {@code getEmpMetricValues / getOrgMetricValues /
 * getCustMetricValues} 替换为真实实现——批量读取指标定义的 {@code val_slot} → 按 base_dim 路由
 * 对应宽表 → 返回 {@code (metricCode -> metricValue)} 映射。
 *
 * <p>V1.3 R2.5 交付 {@link #getUserMetricCards(String)}；V1.4 S3 补 mom/yoy 字段与 cycleKey
 * 精确匹配；V1.5 P3.1 改为按 (metricCode, cycleType) 组合去重，同 metric 不同 cycleType
 * 时生成多张卡片（前端按 metricCode+cycleType 作为唯一键展示）。
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
     * 查询员工工作台指标卡片（V1.5 P3.1: 按 (metricCode, cycleType) 组合去重，修复 V1.4 M01 首命中歧义）.
     *
     * <p>V1.5 P3.1 策略：替换 V1.4 的 {@code codeToCycleType = new LinkedHashMap<>()} 首命中方案为
     * {@code LinkedHashSet<MetricKey>} 组合键去重。同一 metricCode 被多 scheme 不同 cycleType
     * 引用时生成多张卡片（每 cycleType 一张）；同 cycleType 时仍去重。签名 / 契约不变，
     * 前端按 {@code metricCode + cycleType} 作为唯一键展示。
     *
     * <p>流程：
     * <ol>
     *   <li>取所有 ACTIVE KPI 方案，按 {@code (metricCode, cycleType)} 组合键登记到
     *       {@link LinkedHashSet}（首次出现顺序稳定）。</li>
     *   <li>去重后的 distinct metricCode 批量读 {@link MetricDefService#getByCodes}，
     *       仅保留 {@code baseDim=EMP} 且已分配 {@code val_slot} 的指标。</li>
     *   <li>读 {@code sys_control(EMP)} 得到 {@code latestDataDate + currentVersion}，
     *       无可用版本直接返回空列表（fail-safe）。</li>
     *   <li>为每个 {@code MetricKey} 组合调用 {@link #buildCard} 组装单张卡片：
     *       宽表读 actual / previous / yearAgo，计算 mom / yoy / achievementRate。</li>
     * </ol>
     *
     * <p><strong>V1.4 S3.3/S3.4 维持</strong>：每 metric 仍串行 3 次宽表查询（current/previous/yearAgo），
     * N metric 下 3N 查询；batch 合并优化留 V1.5+ M02 处理。
     *
     * @param empId 员工工号
     * @return 指标卡片列表（同一 metricCode 可能出现多次，每 cycleType 一条；可能为空）
     */
    @Override
    public List<MetricCardDTO> getUserMetricCards(String empId) {
        // 1. 取所有 ACTIVE KPI 方案, 无则直接返回（防御 fail-safe, 无方案 = 无卡片）
        List<PerfKpiScheme> schemes = kpiSchemeService.listActiveSchemes();
        if (schemes == null || schemes.isEmpty()) {
            return List.of();
        }

        // 2. V1.5 P3.1: 按 (metricCode, cycleType) 作为组合键去重, 避免 V1.4 首命中歧义.
        //    LinkedHashSet 保持首次出现顺序, 前端展示时顺序稳定.
        LinkedHashSet<MetricKey> metricKeys = new LinkedHashSet<>();
        for (PerfKpiScheme scheme : schemes) {
            List<PerfKpiItem> items = kpiItemService.listBySchemeId(scheme.getId());
            if (items == null) {
                continue;
            }
            for (PerfKpiItem item : items) {
                String code = item.getMetricCode();
                if (code == null) {
                    continue;
                }
                metricKeys.add(new MetricKey(code, scheme.getCycleType()));
            }
        }
        if (metricKeys.isEmpty()) {
            return List.of();
        }

        // 3. 批量读取指标定义 (distinct metricCode 避免重复查询),
        //    过滤非 EMP 维度与未分配 slot 的指标.
        List<String> distinctCodes = metricKeys.stream()
                .map(MetricKey::metricCode)
                .distinct()
                .toList();
        List<PerfMetricDef> defs = metricDefService.getByCodes(distinctCodes);
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

        // 5. 为每个 (metricCode, cycleType) 组合组装卡片
        List<MetricCardDTO> cards = new ArrayList<>(metricKeys.size());
        for (MetricKey key : metricKeys) {
            PerfMetricDef def = codeToDef.get(key.metricCode());
            if (def == null) {
                continue; // 已在 codeToDef 过滤掉（非 EMP / 无 slot）
            }
            cards.add(buildCard(empId, def, key.cycleType(), version, latestDate));
        }
        return cards;
    }

    /**
     * V1.5 P3.1: 单张卡片组装抽取，读 actual / target / previous / yearAgo 并算 mom / yoy / rate.
     *
     * <p>每次调用对单个 {@code (metricCode, cycleType)} 组合生效：
     * <ul>
     *   <li>actual：latestDate 的宽表值</li>
     *   <li>target：按 {@code buildCycleKey(cycleType, latestDate)} 查 perf_target_value</li>
     *   <li>previous：按 {@code calculatePreviousDate(cycleType, latestDate)} 的宽表值</li>
     *   <li>yearAgo：{@code latestDate.minusYears(1)} 的宽表值</li>
     * </ul>
     *
     * <p><strong>V1.5 P4.1 优化</strong>：把原 3 次串行单点 {@code selectSlotValue} 合并为
     * 1 次 {@code selectSlotValuesByDates} IN 查询，20 metric 场景查询数从 60 降至 20（-66%）。
     * 调用方在 Map 内按 key 取值，未命中日期对应 {@code map.get(...) == null}。
     *
     * @param empId      员工工号
     * @param def        指标定义（baseDim=EMP 且 slot 已分配）
     * @param cycleType  scheme.cycleType（YEARLY/QUARTERLY/MONTHLY/WEEKLY 或 null）
     * @param version    sys_control 当前基线版本
     * @param latestDate sys_control 当前基线数据日期
     * @return 组装好的单张 MetricCardDTO
     */
    private MetricCardDTO buildCard(String empId, PerfMetricDef def, String cycleType,
                                    String version, LocalDate latestDate) {
        // V1.5 P4.1：收集 current / previous / yearAgo 三个日期，单次 IN 查询拿回
        LocalDate previousDate = calculatePreviousDate(cycleType, latestDate);
        // V1.5 P5.1：按 cycleType 分支的去年同期日期，WEEKLY 走 -52 周对齐 ISO 周
        LocalDate yearAgoDate = calculateYearAgoDate(cycleType, latestDate);

        List<LocalDate> dates = new ArrayList<>(3);
        if (latestDate != null) {
            dates.add(latestDate);
        }
        if (previousDate != null) {
            dates.add(previousDate);
        }
        if (yearAgoDate != null) {
            dates.add(yearAgoDate);
        }

        Map<LocalDate, BigDecimal> batchValues = empIndexResultMapper.selectSlotValuesByDates(
                empId, dates, version, def.getValSlot());

        BigDecimal actual = latestDate == null ? null : batchValues.get(latestDate);
        BigDecimal previousValue = previousDate == null ? null : batchValues.get(previousDate);
        BigDecimal yearAgoValue = yearAgoDate == null ? null : batchValues.get(yearAgoDate);

        String cycleKey = buildCycleKey(cycleType, latestDate);
        PerfTargetValue tv = perfTargetValueMapper.selectByUniqueKey(
                null, "EMP", empId, cycleKey, def.getMetricCode());
        BigDecimal target = tv == null ? null : tv.getTargetValue();
        BigDecimal rate = null;
        if (target != null && target.compareTo(BigDecimal.ZERO) != 0 && actual != null) {
            rate = actual.multiply(new BigDecimal("100"))
                    .divide(target, 4, RoundingMode.HALF_UP);
        }
        BigDecimal mom = calculateMom(actual, previousValue);
        BigDecimal yoy = calculateYoy(actual, yearAgoValue);
        return MetricCardDTO.builder()
                .metricCode(def.getMetricCode())
                .metricName(def.getMetricName())
                .currentValue(actual)
                .previousValue(previousValue)
                .targetValue(target)
                .achievementRate(rate)
                .mom(mom)
                .yoy(yoy)
                .unit(def.getUnit())
                .dataDate(latestDate)
                .build();
    }

    /**
     * V1.5 P3.1: (metricCode, cycleType) 组合键, 用于 LinkedHashSet 去重.
     *
     * <p>cycleType 可为 null（老数据或未配方案）: record 默认 equals/hashCode 支持 null 字段,
     * {@code (code, null)} 与 {@code (code, "")} 被视为不同组合; {@link #buildCycleKey}
     * 内部对 null cycleType 已按年兜底.
     */
    private record MetricKey(String metricCode, String cycleType) {
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

    @Override
    public LocalDate getLatestDataDate(String scopeDim) {
        if (scopeDim == null || scopeDim.isBlank()) {
            return null;
        }
        SysControl sc = sysControlService.getCurrentVersion(scopeDim.toUpperCase());
        return sc != null ? sc.getLatestDataDate() : null;
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
     * V1.5 P5.1：按 cycleType 分支的"去年同期"日期.
     *
     * <ul>
     *   <li>YEARLY / QUARTERLY / MONTHLY / 兜底 → {@code minusYears(1)}</li>
     *   <li>WEEKLY → {@code minus(52, ChronoUnit.WEEKS)}（对齐 ISO 周次，避免
     *       跨 ISO 年 53 周边界的语义倒置）</li>
     * </ul>
     *
     * @param cycleType 周期类型（大小写不敏感；null 走年兜底）
     * @param date      当期日期（null 时返回 null）
     * @return 去年同期日期
     */
    private LocalDate calculateYearAgoDate(String cycleType, LocalDate date) {
        if (date == null) {
            return null;
        }
        if (cycleType == null) {
            return date.minusYears(1);
        }
        return switch (cycleType.toUpperCase()) {
            case "WEEKLY" -> date.minus(52, ChronoUnit.WEEKS);
            case "YEARLY", "QUARTERLY", "MONTHLY" -> date.minusYears(1);
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
     * V1.4 S3.4: 计算同比变化率 yoy = (current - yearAgo) / |yearAgo| * 100, 保留 2 位小数.
     *
     * <p>除零保护：yearAgo=0 或任一侧为 null 直接返回 null, 同 calculateMom.
     *
     * @param current  当期值
     * @param yearAgo  去年同期值 (date.minusYears(1))
     * @return yoy 百分比 (2 位小数) 或 null
     */
    private BigDecimal calculateYoy(BigDecimal current, BigDecimal yearAgo) {
        if (current == null || yearAgo == null) {
            return null;
        }
        if (yearAgo.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(yearAgo)
                .divide(yearAgo.abs(), 4, RoundingMode.HALF_UP)
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
