package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.MetricQueryApi;
import com.bank.branch.platform.performance.api.dto.CustMetricSnapshotDTO;
import com.bank.branch.platform.performance.api.dto.EmpMetricSnapshotDTO;
import com.bank.branch.platform.performance.api.dto.OrgMetricSnapshotDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.service.SysControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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
        // V1.3 R2.2 Red 占位: Step 3 Green 阶段替换为宽表查询
        throw new UnsupportedOperationException("V1.3 R2.2 Red placeholder");
    }

    @Override
    public List<OrgMetricSnapshotDTO> batchQueryOrgSnapshots(List<String> orgCodes,
                                                             LocalDate dateFrom,
                                                             LocalDate dateTo,
                                                             List<String> metricCodes) {
        // R2.3 未实现, 保持 UOE
        throw new UnsupportedOperationException("V1.2 delivered");
    }

    @Override
    public List<CustMetricSnapshotDTO> batchQueryCustSnapshots(List<String> custIds,
                                                               LocalDate dateFrom,
                                                               LocalDate dateTo,
                                                               List<String> metricCodes) {
        // R2.4 未实现, 保持 UOE
        throw new UnsupportedOperationException("V1.2 delivered");
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
    @SuppressWarnings("unused")
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
    @SuppressWarnings("unused")
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
