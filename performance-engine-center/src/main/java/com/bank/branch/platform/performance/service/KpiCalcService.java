package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.KpiResult;
import com.bank.branch.platform.performance.entity.PerfKpiItem;
import com.bank.branch.platform.performance.entity.PerfKpiScheme;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.event.KpiCalcCompletedEvent;
import com.bank.branch.platform.performance.event.PerfEventPublisher;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.KpiResultMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * KPI 计算服务（Task P4.2）.
 *
 * <p>职责：对单员工 / 单 KPI 方案，按 {@code perf_kpi_item} 的 weight/multiplier/min/max
 * 对宽表 {@code emp_index_result} 的值做加权求和，写入 {@code kpi_result}.
 *
 * <p>计算口径：
 * <ol>
 *   <li>读 scheme + items</li>
 *   <li>对每个 item：
 *       <ul>
 *           <li>按 {@code metric_code} → {@link PerfMetricDef#getValSlot} 定位宽表列</li>
 *           <li>从 {@code emp_index_result} 取原始指标值（缺失以 0 兜底）</li>
 *           <li>构造合成公式 {@code ${metric_code} * multiplier}，委托
 *               {@link KpiFormulaService#eval} 求得 rawScore</li>
 *           <li>clamp 到 {@code [minScore, maxScore]} 得 itemScore</li>
 *       </ul>
 *   </li>
 *   <li>totalScore = Σ(itemScore × weight) / Σ(weight)（weight 总和为 0 抛 KPI_WEIGHT_SUM_INVALID）</li>
 *   <li>detailJson 记录每 item 的 (metricCode, metricValue, weight, multiplier, rawScore, itemScore)</li>
 *   <li>写 kpi_result（由 DB AUTO_INCREMENT 回填 id）</li>
 * </ol>
 *
 * <p><strong>事务边界</strong>：与 {@link MetricCalcService} 一致，<em>不包</em> {@code @Transactional}.
 * 原因：KPI 计算失败时 kpi_result 的失败痕迹依赖独立事务写入，避免大事务回滚导致"跑失败却看不到失败记录".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiCalcService {

    private final KpiSchemeService kpiSchemeService;
    private final KpiItemService kpiItemService;
    private final MetricDefService metricDefService;
    private final EmpIndexResultMapper empIndexResultMapper;
    private final KpiResultMapper kpiResultMapper;
    private final KpiFormulaService kpiFormulaService;
    /** V1.2 Q4.1：批量完成后投递 {@link KpiCalcCompletedEvent}. */
    private final PerfEventPublisher perfEventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 对单员工单 KPI 方案计算一次 KPI.
     *
     * @param empId      员工工号
     * @param schemeCode KPI 方案编码
     * @param cycleType  周期类型（MONTHLY / QUARTERLY）
     * @param cycleDate  周期对应日期（如月末）
     * @param asOfDate   计算基准日（与宽表 data_date 对齐）
     * @param version    数据版本（与宽表 version 对齐）
     * @return kpi_result 主键 id（DB AUTO_INCREMENT 回填）
     * @throws PerfException 方案不存在 / weight 总和=0 / metric 不存在 / 未分配 slot / 公式计算异常
     */
    public Long calcSingleEmp(String empId,
                              String schemeCode,
                              String cycleType,
                              LocalDate cycleDate,
                              LocalDate asOfDate,
                              String version) {
        PerfKpiScheme scheme = kpiSchemeService.getBySchemeCodeOrNull(schemeCode)
                .orElseThrow(() -> new PerfException(PerfErrorCode.KPI_SCHEME_NOT_FOUND, schemeCode));

        List<PerfKpiItem> items = Optional.ofNullable(kpiItemService.listBySchemeId(scheme.getId()))
                .orElse(List.of());

        // 权重总和校验（= 0 视为无效配置）
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (PerfKpiItem it : items) {
            BigDecimal w = it.getWeight() == null ? BigDecimal.ZERO : it.getWeight();
            totalWeight = totalWeight.add(w);
        }
        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PerfException(PerfErrorCode.KPI_WEIGHT_SUM_INVALID,
                    "schemeCode=" + schemeCode);
        }

        List<Map<String, Object>> details = new ArrayList<>();
        BigDecimal weightedSum = BigDecimal.ZERO;

        for (PerfKpiItem item : items) {
            String metricCode = item.getMetricCode();
            PerfMetricDef def = metricDefService.getByCodeOrNull(metricCode);
            if (def == null) {
                throw new PerfException(PerfErrorCode.METRIC_NOT_FOUND, metricCode);
            }
            Integer slot = def.getValSlot();
            if (slot == null || slot < 1 || slot > 200) {
                throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                        "metric=" + metricCode + " 未分配合法 val_slot");
            }

            // 从宽表取原始指标值（缺失以 0 兜底，保证公式可计算）
            BigDecimal rawMetric = empIndexResultMapper.selectSlotValue(empId, asOfDate, version, slot);
            if (rawMetric == null) {
                rawMetric = BigDecimal.ZERO;
            }

            // 构造合成公式：${metric_code} * multiplier；multiplier 默认 1
            BigDecimal multiplier = item.getMultiplier() == null
                    ? BigDecimal.ONE : item.getMultiplier();
            String formula = "${" + metricCode + "} * " + multiplier.toPlainString();
            Map<String, BigDecimal> vars = Map.of(metricCode, rawMetric);
            BigDecimal rawScore = kpiFormulaService.eval(formula, vars);

            // clamp 到 [minScore, maxScore]
            BigDecimal min = item.getMinScore() == null ? BigDecimal.ZERO : item.getMinScore();
            BigDecimal max = item.getMaxScore() == null
                    ? new BigDecimal("999999") : item.getMaxScore();
            BigDecimal itemScore = rawScore;
            if (itemScore.compareTo(min) < 0) {
                itemScore = min;
            }
            if (itemScore.compareTo(max) > 0) {
                itemScore = max;
            }

            BigDecimal weight = item.getWeight() == null ? BigDecimal.ZERO : item.getWeight();
            weightedSum = weightedSum.add(itemScore.multiply(weight));

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("metricCode", metricCode);
            detail.put("metricValue", rawMetric);
            detail.put("weight", weight);
            detail.put("multiplier", multiplier);
            detail.put("rawScore", rawScore);
            detail.put("itemScore", itemScore);
            details.add(detail);
        }

        // 归一化总分 = Σ(itemScore * weight) / Σ(weight)，保留 4 位小数
        BigDecimal totalScore = weightedSum.divide(totalWeight, 4, RoundingMode.HALF_UP);

        KpiResult r = new KpiResult();
        r.setEmpId(empId);
        r.setCycleType(cycleType);
        r.setCycleDate(cycleDate);
        r.setAsOfDate(asOfDate);
        r.setDataVersion(version);
        r.setKpiTotalScore(totalScore);
        r.setDetailJson(toJson(Map.of(
                "schemeCode", schemeCode,
                "schemeId", scheme.getId(),
                "items", details,
                "totalScore", totalScore
        )));
        kpiResultMapper.insert(r);
        log.info("[KpiCalc] emp={} scheme={} cycle={} asOf={} totalScore={} (items={})",
                empId, schemeCode, cycleDate, asOfDate, totalScore, items.size());
        return r.getId();
    }

    /**
     * 对方案内全部员工批量计算 KPI.
     *
     * <p>员工集合策略：取 {@code emp_index_result} 中 {@code (data_date=asOfDate, version)}
     * 维度下的 distinct empId。当前版本不区分方案员工范围；若 V1.2 引入"方案适用员工"表，
     * 只需在本方法增加一层过滤即可。
     *
     * <p>失败隔离：单个员工计算失败只打 warn 日志，<em>不中断</em>后续员工；返回成功数量由调用方决策是否告警。
     *
     * @param schemeCode 方案编码
     * @param cycleType  周期类型
     * @param cycleDate  周期对应日期
     * @param asOfDate   计算基准日（对齐宽表 data_date）
     * @param version    数据版本
     * @return 计算成功的员工数
     */
    public int calcScheme(String schemeCode,
                          String cycleType,
                          LocalDate cycleDate,
                          LocalDate asOfDate,
                          String version) {
        List<String> empIds = empIndexResultMapper.selectDistinctEmpIds(asOfDate, version);
        if (empIds == null || empIds.isEmpty()) {
            // 宽表无任何员工数据：视作"批次未真正启动"，不发事件，下游也无需刷新
            log.info("[KpiCalc] scheme={} asOf={} 无员工数据，跳过", schemeCode, asOfDate);
            return 0;
        }
        int success = 0;
        for (String empId : empIds) {
            try {
                calcSingleEmp(empId, schemeCode, cycleType, cycleDate, asOfDate, version);
                success++;
            } catch (Exception ex) {
                // 单员工失败隔离：不中断，吞掉异常继续算；最终由事件 empCount 字段反映成功数
                log.warn("[KpiCalc] scheme={} emp={} 计算失败: {}",
                        schemeCode, empId, ex.getMessage());
            }
        }
        log.info("[KpiCalc] scheme={} asOf={} 批量计算完成 success={}/total={}",
                schemeCode, asOfDate, success, empIds.size());

        // V1.2 Q4.1：批次已启动即发事件（empCount=本批成功落地员工数）。
        // 即便 success=0 也发，让下游得知本次批次终态，避免"悬挂在运行中"的观察者等待。
        perfEventPublisher.publish(new KpiCalcCompletedEvent(
                MDC.get("traceId"),
                schemeCode,
                cycleType,
                cycleDate,
                asOfDate,
                version,
                success));
        return success;
    }

    /**
     * 序列化 detail 到 JSON 字符串；失败时退化为 toString，避免阻断主流程.
     *
     * @param payload 待序列化对象
     * @return JSON 字符串
     */
    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("[KpiCalc] detailJson 序列化失败: {}", ex.getMessage());
            return String.valueOf(payload);
        }
    }
}
