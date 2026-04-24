package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.MetricQueryApi;
import com.bank.branch.platform.performance.api.dto.CustMetricSnapshotDTO;
import com.bank.branch.platform.performance.api.dto.EmpMetricSnapshotDTO;
import com.bank.branch.platform.performance.api.dto.OrgMetricSnapshotDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpMetricValueRow;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.service.SysControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表专用指标批量查询 API 实现.
 *
 * <p>V1.3 R2.2 交付：{@link #batchQueryEmpSnapshots} 替换为真实实现（按 slot 批查 EMP 宽表）.
 * V1.3 R2.3/R2.4 交付：{@link #batchQueryOrgSnapshots} / {@link #batchQueryCustSnapshots}.
 *
 * <p>批量上限：empIds/orgCodes/custIds &le; 500，metricCodes &le; 50。超限抛
 * {@link PerfErrorCode#BATCH_QUERY_EXCEEDS_LIMIT}.
 *
 * <p>V1.3 初版仅支持单日点查询（dateFrom 必须等于 dateTo，Facade 以 dateFrom 为查询日期）；
 * 跨日期区间查询留 V1.4 迭代补齐（每日循环 + 去重合并）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricQueryApiImpl implements MetricQueryApi {

    /** 批量查询 subject 上限（来自 §K BATCH_QUERY_EXCEEDS_LIMIT 契约 500 条）. */
    private static final int MAX_SUBJECT_BATCH = 500;

    /** 批量查询 metricCode 上限（保持与 MetricQueryApi 头部注释一致 50）. */
    private static final int MAX_METRIC_CODE_BATCH = 50;

    private final MetricDefService metricDefService;
    private final SysControlService sysControlService;
    private final EmpIndexResultMapper empIndexResultMapper;
    private final OrgIndexResultMapper orgIndexResultMapper;
    private final CustIndexResultMapper custIndexResultMapper;

    @Override
    public List<EmpMetricSnapshotDTO> batchQueryEmpSnapshots(List<String> empIds,
                                                             LocalDate dateFrom,
                                                             LocalDate dateTo,
                                                             List<String> metricCodes) {
        validateBatch(empIds, MAX_SUBJECT_BATCH, metricCodes, MAX_METRIC_CODE_BATCH);
        LocalDate dataDate = validateAndGetDate(dateFrom, dateTo);

        Map<String, PerfMetricDef> defs = loadMetricDefsForDim(metricCodes, "EMP");
        if (defs.isEmpty()) {
            return List.of();
        }

        // 从 sys_control 取当前 EMP 维度版本（V1.3 R2.2 简化: 不允许调用方指定 version）
        SysControl sc = sysControlService.getCurrentVersion("EMP");
        String version = sc == null ? null : sc.getCurrentVersion();

        // 按 metric 轮询, 累积每个 empId 的 (code -> value) 映射
        Map<String, Map<String, BigDecimal>> empToCodeToValue = new LinkedHashMap<>();
        for (Map.Entry<String, PerfMetricDef> entry : defs.entrySet()) {
            String code = entry.getKey();
            Integer slot = entry.getValue().getValSlot();
            List<EmpMetricValueRow> rows = empIndexResultMapper.selectSlotValuesByEmps(
                    empIds, dataDate, version, slot);
            if (rows == null) {
                continue;
            }
            for (EmpMetricValueRow row : rows) {
                if (row.getMetricValue() == null) {
                    continue;
                }
                empToCodeToValue
                        .computeIfAbsent(row.getEmpId(), k -> new LinkedHashMap<>())
                        .put(code, row.getMetricValue());
            }
        }

        // 组装 DTO 列表（输入 empIds 顺序保留）
        List<EmpMetricSnapshotDTO> result = new ArrayList<>(empToCodeToValue.size());
        for (String empId : empIds) {
            Map<String, BigDecimal> values = empToCodeToValue.get(empId);
            if (values == null) {
                continue;
            }
            result.add(EmpMetricSnapshotDTO.builder()
                    .empId(empId)
                    .dataDate(dataDate)
                    .version(version)
                    .metricValues(values)
                    .build());
        }
        return result;
    }

    @Override
    public List<OrgMetricSnapshotDTO> batchQueryOrgSnapshots(List<String> orgCodes,
                                                             LocalDate dateFrom,
                                                             LocalDate dateTo,
                                                             List<String> metricCodes) {
        // V1.3 R2.3 Red 占位: Green 阶段替换为宽表查询
        throw new UnsupportedOperationException("V1.3 R2.3 Red placeholder");
    }

    @Override
    public List<CustMetricSnapshotDTO> batchQueryCustSnapshots(List<String> custIds,
                                                               LocalDate dateFrom,
                                                               LocalDate dateTo,
                                                               List<String> metricCodes) {
        // V1.3 R2.4 Red 占位: Green 阶段替换为宽表查询
        throw new UnsupportedOperationException("V1.3 R2.4 Red placeholder");
    }

    /**
     * 校验批量上限（subject + metricCode 两个维度）.
     */
    private static void validateBatch(List<String> subjects, int subjectLimit,
                                      List<String> codes, int codeLimit) {
        if (subjects == null || subjects.isEmpty()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "subjects 不能为空");
        }
        if (subjects.size() > subjectLimit) {
            throw new PerfException(PerfErrorCode.BATCH_QUERY_EXCEEDS_LIMIT,
                    "subjects 数量=" + subjects.size() + " 超上限 " + subjectLimit);
        }
        if (codes == null || codes.isEmpty()) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "metricCodes 不能为空");
        }
        if (codes.size() > codeLimit) {
            throw new PerfException(PerfErrorCode.BATCH_QUERY_EXCEEDS_LIMIT,
                    "metricCodes 数量=" + codes.size() + " 超上限 " + codeLimit);
        }
    }

    /**
     * 校验日期参数（V1.3 仅支持单日点查询，后续 V1.4 支持跨日期）.
     */
    private static LocalDate validateAndGetDate(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "dateFrom 不能为空");
        }
        if (dateTo != null && !dateTo.equals(dateFrom)) {
            // V1.3 只支持单日快照; 跨日期留 V1.4 迭代
            log.warn("[MetricQueryApi] V1.3 仅支持 dateFrom==dateTo 的点查询, 收到 dateFrom={} dateTo={}, 以 dateFrom 为准",
                    dateFrom, dateTo);
        }
        return dateFrom;
    }

    /**
     * 统一加载 metricDef 且按维度过滤, 返回 (metricCode -> def) 映射，
     * 同时只保留 baseDim == expectedDim 且已分配 slot 的指标.
     */
    private Map<String, PerfMetricDef> loadMetricDefsForDim(List<String> metricCodes, String expectedDim) {
        Map<String, PerfMetricDef> defs = new LinkedHashMap<>();
        List<PerfMetricDef> loaded = metricDefService.getByCodes(metricCodes);
        if (loaded == null) {
            return defs;
        }
        for (PerfMetricDef def : loaded) {
            if (def.getValSlot() == null) {
                log.debug("[MetricQueryApi] 指标 {} 未分配 slot, 跳过", def.getMetricCode());
                continue;
            }
            if (!expectedDim.equalsIgnoreCase(def.getBaseDim())) {
                log.debug("[MetricQueryApi] 指标 {} baseDim={} 与请求维度 {} 不符, 跳过",
                        def.getMetricCode(), def.getBaseDim(), expectedDim);
                continue;
            }
            defs.put(def.getMetricCode(), def);
        }
        return defs;
    }
}
