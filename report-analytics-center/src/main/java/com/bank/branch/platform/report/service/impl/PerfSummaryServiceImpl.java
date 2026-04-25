package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.api.KpiApi;
import com.bank.branch.platform.report.dto.req.PerfSummaryReqDTO;
import com.bank.branch.platform.report.dto.resp.ExportTaskRespDTO;
import com.bank.branch.platform.report.dto.resp.PerfSummaryRowVO;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.service.ExportTaskService;
import com.bank.branch.platform.report.service.PerfSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link PerfSummaryService} 实现（Task M3.2.1，Green）.
 *
 * <p>装配步骤（plan L2080-L2117 + 真实 API 适配）：
 * <ol>
 *   <li>subjectIds.size 校验：&gt; 100 抛 RPT-40007</li>
 *   <li>单条循环调 KpiApi.getCurrentKpiTotal（V1.1 P4.3 已真实交付，返回 BigDecimal 或 null）</li>
 *   <li>null 跳过（员工无 KPI 数据），异常包装 RPT-50001（R4 fail-close）</li>
 *   <li>装配 PerfSummaryRowVO，内存分页（V1 简化）</li>
 * </ol>
 *
 * <p>缓存：{@code rpt:summary:perf}，key 按 dim + cycleType + subjectIds.size + first/last 区分.
 *
 * <p>V1.1+ 计划：若 performance.KpiApi.batchGetKpiTotal 落地，本类切批量（已登记技术债 M6.3）.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfSummaryServiceImpl implements PerfSummaryService {

    private static final int MAX_SUBJECTS = 100;

    private final KpiApi kpiApi;

    private final CurrentUserApi currentUserApi;

    private final ExportTaskService exportTaskService;

    @Override
    @Cacheable(value = "rpt:summary:perf",
            key = "T(java.lang.String).format('%s:%s:%d', #req.dim, #req.cycleType, #req.subjectIds.hashCode())",
            unless = "#result == null")
    public PageResult<PerfSummaryRowVO> getPerfSummary(PerfSummaryReqDTO req, PageRequest page) {
        // 1) 大小校验
        if (req.getSubjectIds().size() > MAX_SUBJECTS) {
            log.warn("[PerfSummary] subjectIds 超限 size={} max={}", req.getSubjectIds().size(), MAX_SUBJECTS);
            throw new RptException(RptErrorCode.SUBJECT_SIZE_EXCEEDED);
        }

        // 2) 单条循环调 KpiApi（V1.0 简化，performance V1.1 P4.3 真实交付返回 BigDecimal）
        List<PerfSummaryRowVO> rows = new ArrayList<>(req.getSubjectIds().size());
        for (String subjectId : req.getSubjectIds()) {
            try {
                BigDecimal totalScore = kpiApi.getCurrentKpiTotal(subjectId, req.getCycleType());
                // 3) null → 该员工/机构无 KPI 数据，跳过
                if (totalScore == null) {
                    log.debug("[PerfSummary] subjectId={} cycleType={} 无 KPI 数据，跳过",
                            subjectId, req.getCycleType());
                    continue;
                }
                rows.add(PerfSummaryRowVO.builder()
                        .subjectId(subjectId)
                        .subjectName(subjectId)  // V1.0 简化：直接用 subjectId 兜底
                        .cycleType(req.getCycleType())
                        .totalScore(totalScore)
                        .build());
            } catch (RuntimeException ex) {
                log.warn("[PerfSummary] KpiApi.getCurrentKpiTotal 失败 subjectId={} cycleType={} cause={}",
                        subjectId, req.getCycleType(), ex.getMessage());
                throw new RptException(RptErrorCode.CROSS_MODULE_CALL_FAILED, ex);
            }
        }
        return PageResult.of(page.getPageNo(), page.getPageSize(), rows.size(), rows);
    }

    @Override
    public ExportTaskRespDTO submitPerfSummaryExport(PerfSummaryReqDTO req) {
        return exportTaskService.submitPerfSummaryExport(req);
    }
}
