package com.bank.branch.platform.report.service.impl;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.TouchTaskQueryApi;
import com.bank.branch.platform.customer.api.dto.TouchTaskSummaryDTO;
import com.bank.branch.platform.report.dto.req.TouchSummaryReqDTO;
import com.bank.branch.platform.report.dto.resp.ExportTaskRespDTO;
import com.bank.branch.platform.report.dto.resp.ReportTouchOrgVO;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.service.ExportTaskService;
import com.bank.branch.platform.report.service.TouchSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * {@link TouchSummaryService} 实现（Task M3.1.1，Green）.
 *
 * <p>装配步骤（plan L1855-L2001 + 真实 API 适配）：
 * <ol>
 *   <li>入参校验：日期范围 &le; 366 天（08 §2.1 max.date.range.days），超限 RPT-40006</li>
 *   <li>DataScope：未传 orgId 时按当前用户 orgCode 兜底</li>
 *   <li>上游调用：customer.TouchTaskQueryApi.getOrgTouchSummary（真实签名 String/String/String → 单个 DTO），
 *       异常包装 RPT-50001（R4 fail-close）</li>
 *   <li>VO 装配：上游 TouchTaskSummaryDTO → ReportTouchOrgVO，含 successRate 4 位小数</li>
 *   <li>单机构返回单行，内存分页（V1 简化）</li>
 * </ol>
 *
 * <p>缓存：{@code rpt:summary:touch}，key 按 orgId + startDate + endDate 组合.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TouchSummaryServiceImpl implements TouchSummaryService {

    private static final int MAX_RANGE_DAYS = 366;

    private static final int RATE_SCALE = 4;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final TouchTaskQueryApi touchTaskQueryApi;

    private final CurrentUserApi currentUserApi;

    private final OrgApi orgApi;

    private final ExportTaskService exportTaskService;

    @Override
    @Cacheable(value = "rpt:summary:touch",
            key = "T(java.lang.String).format('%s:%s:%s', #req.orgId, #req.startDate, #req.endDate)",
            unless = "#result == null")
    public PageResult<ReportTouchOrgVO> getOrgTouchSummary(TouchSummaryReqDTO req, PageRequest page) {
        // 1) 日期范围校验
        long days = ChronoUnit.DAYS.between(req.getStartDate(), req.getEndDate());
        if (days > MAX_RANGE_DAYS) {
            log.warn("[TouchSummary] 日期范围超限 days={} max={}", days, MAX_RANGE_DAYS);
            throw new RptException(RptErrorCode.METRIC_DIM_MISMATCH);
        }
        // 2) 未传 orgId 时按当前用户 orgCode 兜底
        String orgCode = StringUtils.hasText(req.getOrgId())
                ? req.getOrgId()
                : currentUserApi.getCurrentOrgCode();
        String startStr = req.getStartDate().format(DATE_FORMATTER);
        String endStr = req.getEndDate().format(DATE_FORMATTER);

        // 3) 上游调用 + 异常包装
        TouchTaskSummaryDTO upstream;
        try {
            upstream = touchTaskQueryApi.getOrgTouchSummary(orgCode, startStr, endStr);
        } catch (RuntimeException ex) {
            log.warn("[TouchSummary] 上游触达汇总查询失败 orgCode={} startDate={} endDate={} cause={}",
                    orgCode, startStr, endStr, ex.getMessage());
            throw new RptException(RptErrorCode.CROSS_MODULE_CALL_FAILED, ex);
        }
        if (upstream == null) {
            return PageResult.of(page.getPageNo(), page.getPageSize(), 0L, List.of());
        }
        // 4) VO 装配
        ReportTouchOrgVO vo = toVO(upstream);
        return PageResult.of(page.getPageNo(), page.getPageSize(), 1L, List.of(vo));
    }

    @Override
    public ExportTaskRespDTO submitTouchSummaryExport(TouchSummaryReqDTO req) {
        return exportTaskService.submitTouchSummaryExport(req);
    }

    /**
     * 上游 {@link TouchTaskSummaryDTO} → {@link ReportTouchOrgVO}.
     *
     * <p>failedCount 口径：customer 上游无 failed 字段，本 VO 用 cancelledCount 回填
     * （plan 设计口径，03 §C.2 对 "失败" 的语义涵盖 CANCELLED）.
     */
    private ReportTouchOrgVO toVO(TouchTaskSummaryDTO dto) {
        long total = dto.getTotalCount() != null ? dto.getTotalCount() : 0L;
        long success = dto.getSuccessCount() != null ? dto.getSuccessCount() : 0L;
        long cancelled = dto.getCancelledCount() != null ? dto.getCancelledCount() : 0L;
        BigDecimal rate = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(success).divide(BigDecimal.valueOf(total), RATE_SCALE, RoundingMode.HALF_UP);
        return ReportTouchOrgVO.builder()
                .orgCode(dto.getOrgId())
                .orgName(resolveOrgName(dto.getOrgId(), dto.getOrgName()))
                .totalTask((int) total)
                .successCount((int) success)
                .failedCount((int) cancelled)
                .successRate(rate)
                .build();
    }

    /**
     * 解析机构名：优先用上游返回 orgName，否则 OrgApi 反查，最后回填 orgCode.
     */
    private String resolveOrgName(String orgCode, String upstreamName) {
        if (StringUtils.hasText(upstreamName)) {
            return upstreamName;
        }
        try {
            OrgDTO org = orgApi.getOrg(orgCode);
            return org != null && StringUtils.hasText(org.getOrgName()) ? org.getOrgName() : orgCode;
        } catch (RuntimeException e) {
            log.warn("[TouchSummary] OrgApi.getOrg 失败 orgCode={} cause={}", orgCode, e.getMessage());
            return orgCode;
        }
    }
}
