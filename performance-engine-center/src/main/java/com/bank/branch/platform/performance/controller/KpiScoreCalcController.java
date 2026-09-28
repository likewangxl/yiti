package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.performance.controller.dto.KpiScoreCalcReqDTO;
import com.bank.branch.platform.performance.controller.dto.KpiScoreDetailExportRow;
import com.bank.branch.platform.performance.controller.dto.KpiScoreGroupPageDTO;
import com.bank.branch.platform.performance.controller.dto.KpiScoreGroupRowDTO;
import com.bank.branch.platform.performance.controller.dto.KpiScoreMetricCellDTO;
import com.bank.branch.platform.performance.controller.dto.KpiScoreStatsDTO;
import com.bank.branch.platform.performance.controller.dto.MetricOptionDTO;
import com.bank.branch.platform.performance.controller.dto.PerfKpiCalcLogDTO;
import com.bank.branch.platform.performance.controller.dto.PerfKpiScoreResultDTO;
import com.bank.branch.platform.performance.service.KpiScoreCalcService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * KPI 分值计算接口（手动触发 / 前端重算）.
 *
 * <p>{@code POST /api/perf/kpi-score/calc}：按数据日期（必填）+ KPI 方案编码（可空，空=全部 ACTIVE 方案）
 * 重新计算 KPI 得分并 upsert 到 {@code PERF_KPI_SCORE}。既供运维手动触发，也供前端"重算单个 KPI"。
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/kpi-score")
@RequiredArgsConstructor
@Tag(name = "KPI分值计算", description = "按数据日期 + KPI方案重算 KPI 得分")
public class KpiScoreCalcController {

    private final KpiScoreCalcService kpiScoreCalcService;
    private final CurrentUserApi currentUserApi;
    private final UserApi userApi;
    private final AuditApi auditApi;

    /**
     * 触发 KPI 分值计算（考核计算页面"触发"按钮）.
     *
     * <p>请求体含 数据日期 + KPI方案编码 + 触发原因；触发原因由 {@code @AuditLog} 切面记入审批日志。
     *
     * @param req 触发请求（数据日期 / 方案编码 / 触发原因，均必填）
     * @return 任务流水 ID
     */
    @PostMapping("/calc")
    @Operation(summary = "触发/重算 KPI 得分（先记审计日志再计算）")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXECUTE)
    public ResponseWrapper<String> calc(@Valid @RequestBody KpiScoreCalcReqDTO req,
                                        HttpServletRequest request) {
        LocalDate dt = LocalDate.parse(req.getDataDate());
        // 触发人工号 = 当前用户 empId 解析出的 PT_USER.username（约定：工号=username）；姓名取 displayName
        String empId = currentUserApi.getCurrentEmpId();
        String triggerBy = empId;
        String empName = null;
        if (empId != null) {
            UserDTO u = userApi.getUserByEmpId(empId);
            if (u != null) {
                if (u.getUsername() != null) {
                    triggerBy = u.getUsername();
                }
                empName = u.getDisplayName();
            }
        }

        // 先记录触发审计日志（AuditApi.log 走 REQUIRES_NEW 独立事务，确保计算开始前先留痕），再调用计算服务
        auditApi.log(AuditLogCmd.builder()
                .traceId(MDC.get("traceId"))
                .empId(empId)
                .empName(empName)
                .bizType(BizType.PERF_CONFIG.getCode())
                .bizAction(BizAction.EXECUTE.name())
                .resourceUrl(request.getRequestURI())
                .requestMethod(request.getMethod())
                .requestParams("dataDate=" + req.getDataDate() + ", schemeCode=" + req.getSchemeCode())
                .responseStatus(200)
                .reason(req.getReason())
                .build());

        log.info("[KpiScoreCalcController.calc] 审计已记录，开始计算 dataDate={}, schemeCode={}, triggerBy={}, reason={}",
                dt, req.getSchemeCode(), triggerBy, req.getReason());
        String taskId = kpiScoreCalcService.calculate(dt, req.getSchemeCode(), "MANUAL", triggerBy);
        return ResponseWrapper.success(taskId);
    }

    /**
     * 考核计算页面统计：最后一次 KPI 计算任务的成功数/失败数/耗时 + 本月 KPI 计算任务数.
     *
     * @return 统计 DTO
     */
    @GetMapping("/stats")
    @Operation(summary = "考核计算统计（最后一次KPI任务 + 本月任务数）")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<KpiScoreStatsDTO> stats() {
        return ResponseWrapper.success(kpiScoreCalcService.getStats());
    }

    /**
     * 考核计算数据列表：KPI 方案级计算记录（PERF_KPI_CALC_LOG），按数据日期 + KPI方案过滤.
     *
     * @param dataDate   数据日期（yyyy-MM-dd，可空）
     * @param schemeCode KPI 方案编码（可空）
     * @param pageNo     页码
     * @param pageSize   每页条数
     * @return 分页记录
     */
    @GetMapping("/logs")
    @Operation(summary = "KPI方案级计算记录列表")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<PerfKpiCalcLogDTO> logs(
            @RequestParam(value = "dataDate", required = false) String dataDate,
            @RequestParam(value = "schemeCode", required = false) String schemeCode,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        LocalDate dt = (dataDate != null && !dataDate.isEmpty()) ? LocalDate.parse(dataDate) : null;
        PageResult<PerfKpiCalcLogDTO> page = kpiScoreCalcService.pageLogs(dt, schemeCode, pageNo, pageSize);
        return ResponseWrapper.page(page);
    }

    /**
     * 考核计算记录最大数据日期：供前端进入页面时默认选中并展示最新一日数据列表.
     *
     * @return 最大 data_date（yyyy-MM-dd）；无记录时为 null
     */
    @GetMapping("/logs/latest-date")
    @Operation(summary = "KPI计算记录最大数据日期")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<String> latestLogDate() {
        LocalDate d = kpiScoreCalcService.getLatestLogDataDate();
        return ResponseWrapper.success(d == null ? null : d.toString());
    }

    /**
     * KPI 计算结果详情：某数据日期 + KPI方案下的计分明细（PERF_KPI_SCORE），
     * 列含 维度 / 指标 / 对象 / 得分，分页.
     *
     * @param dataDate   数据日期（yyyy-MM-dd，必填）
     * @param schemeCode KPI 方案编码（必填）
     * @param pageNo     页码
     * @param pageSize   每页条数
     * @param orgCode    可选机构编码；传入时必须同时明确 dataDate 和 schemeCode
     * @return 分页计分明细
     */
    @GetMapping("/results")
    @Operation(summary = "KPI计算结果详情（按对象分组：对象/姓名/考核得分 + 各指标 实际/目标/基础/完成率/得分）")
    @BizAuth(bizType = BizType.KPI_CALC, action = BizAction.READ)
    public ResponseWrapper<KpiScoreGroupPageDTO> results(
            @RequestParam(value = "dataDate", required = false) String dataDate,
            @RequestParam(value = "schemeCode", required = false) String schemeCode,
            @RequestParam(value = "subjectType", required = false) String subjectType,
            @RequestParam(value = "subjectKeyword", required = false) String subjectKeyword,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(value = "orgCode", required = false) String orgCode) {
        LocalDate dt = orgCode == null
                ? ((dataDate != null && !dataDate.isEmpty()) ? LocalDate.parse(dataDate) : null)
                : (StringUtils.hasText(dataDate) ? LocalDate.parse(dataDate.trim()) : null);
        KpiScoreGroupPageDTO page = orgCode == null
                ? kpiScoreCalcService.pageScoreGroups(
                        dt, schemeCode, subjectType, subjectKeyword, pageNo, pageSize)
                : kpiScoreCalcService.pageScoreGroups(
                        dt, schemeCode, subjectType, subjectKeyword, orgCode, pageNo, pageSize);
        return ResponseWrapper.success(page);
    }

    /** 维度展示标签. */
    private static String dimLabel(String t) {
        if ("EMP".equals(t)) {
            return "员工";
        }
        if ("ORG".equals(t)) {
            return "机构";
        }
        if ("CUST".equals(t)) {
            return "客户";
        }
        return t == null ? "" : t;
    }

    private void writeXlsxHeader(HttpServletResponse response, String fileName) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
    }

    /**
     * 导出KPI得分（页面数据结果格式 / 按对象分组的透视表）.
     */
    @GetMapping("/export-scores")
    @Operation(summary = "导出KPI得分（页面透视格式 Excel）")
    @BizAuth(bizType = BizType.KPI_CALC, action = BizAction.EXPORT)
    public void exportScores(@RequestParam(value = "dataDate", required = false) String dataDate,
                             @RequestParam(value = "schemeCode", required = false) String schemeCode,
                             @RequestParam(value = "subjectType", required = false) String subjectType,
                             HttpServletResponse response) throws IOException {
        log.info("[KpiScoreCalcController.exportScores] dataDate={}, schemeCode={}, subjectType={}",
                dataDate, schemeCode, subjectType);
        LocalDate dt = (dataDate != null && !dataDate.isEmpty()) ? LocalDate.parse(dataDate) : null;
        writeXlsxHeader(response, "KPI得分_" + (dataDate == null ? "" : dataDate) + ".xlsx");

        KpiScoreGroupPageDTO data = kpiScoreCalcService.exportScoreGroups(
                dt, schemeCode, subjectType, KpiScoreCalcService.EXPORT_ROWS_CAP);
        List<MetricOptionDTO> metrics = data.getMetrics() == null ? List.of() : data.getMetrics();

        // 动态两级表头：固定列 + 每指标 5 子列（指标名跨列合并）
        // 注意：内层 List 必须可变（new ArrayList），EasyExcel 的 ExcelHeadProperty.initHeadRowNumber
        // 会对深度不足的列 .add() 补齐表头行数；若用 List.of() 不可变会抛 UnsupportedOperationException。
        List<List<String>> head = new ArrayList<>();
        head.add(new ArrayList<>(List.of("维度")));
        head.add(new ArrayList<>(List.of("对象ID")));
        head.add(new ArrayList<>(List.of("姓名")));
        head.add(new ArrayList<>(List.of("考核得分")));
        for (MetricOptionDTO m : metrics) {
            String mn = m.getMetricName() != null ? m.getMetricName() : m.getMetricCode();
            head.add(new ArrayList<>(List.of(mn, "实际值")));
            head.add(new ArrayList<>(List.of(mn, "目标值")));
            head.add(new ArrayList<>(List.of(mn, "基础值")));
            head.add(new ArrayList<>(List.of(mn, "完成率(%)")));
            head.add(new ArrayList<>(List.of(mn, "权重")));
            head.add(new ArrayList<>(List.of(mn, "得分")));
        }
        List<List<Object>> rows = new ArrayList<>();
        for (KpiScoreGroupRowDTO r : (data.getRecords() == null ? List.<KpiScoreGroupRowDTO>of() : data.getRecords())) {
            List<Object> row = new ArrayList<>();
            row.add(dimLabel(r.getSubjectType()));
            row.add(r.getSubjectId());
            row.add(r.getSubjectName());
            row.add(r.getTotalScore());
            for (MetricOptionDTO m : metrics) {
                KpiScoreMetricCellDTO c = r.getMetrics() == null ? null : r.getMetrics().get(m.getMetricCode());
                row.add(c == null ? null : c.getActual());
                row.add(c == null ? null : c.getTarget());
                row.add(c == null ? null : c.getBase());
                row.add(c == null ? null : c.getCompleteRate());
                // 权重为该 KPI 指标列固有值（与对象行无关），各行重复输出
                row.add(m.getWeight());
                row.add(c == null ? null : c.getScore());
            }
            rows.add(row);
        }
        EasyExcel.write(response.getOutputStream()).head(head).sheet("KPI得分").doWrite(rows);
    }

    /**
     * 导出KPI明细数据（PERF_KPI_SCORE 表明细记录，平铺）.
     */
    @GetMapping("/export-details")
    @Operation(summary = "导出KPI明细数据（PERF_KPI_SCORE 明细 Excel）")
    @BizAuth(bizType = BizType.KPI_CALC, action = BizAction.EXPORT)
    public void exportDetails(@RequestParam(value = "dataDate", required = false) String dataDate,
                              @RequestParam(value = "schemeCode", required = false) String schemeCode,
                              @RequestParam(value = "subjectType", required = false) String subjectType,
                              HttpServletResponse response) throws IOException {
        log.info("[KpiScoreCalcController.exportDetails] dataDate={}, schemeCode={}, subjectType={}",
                dataDate, schemeCode, subjectType);
        LocalDate dt = (dataDate != null && !dataDate.isEmpty()) ? LocalDate.parse(dataDate) : null;
        writeXlsxHeader(response, "KPI明细_" + (dataDate == null ? "" : dataDate) + ".xlsx");

        List<PerfKpiScoreResultDTO> details = kpiScoreCalcService.listScoresForExport(
                dt, schemeCode, subjectType, KpiScoreCalcService.EXPORT_ROWS_CAP);
        List<KpiScoreDetailExportRow> out = new ArrayList<>(details.size());
        for (PerfKpiScoreResultDTO d : details) {
            KpiScoreDetailExportRow e = new KpiScoreDetailExportRow();
            e.setDataDate(d.getDataDate() == null ? null : d.getDataDate().toString());
            e.setSchemeCode(d.getSchemeCode());
            e.setDimLabel(dimLabel(d.getSubjectType()));
            e.setSubjectId(d.getSubjectId());
            e.setSubjectName(d.getSubjectName());
            e.setMetricCode(d.getMetricCode());
            e.setMetricName(d.getMetricName());
            e.setActualValue(d.getActualValue());
            e.setTargetValue(d.getTargetValue());
            e.setBaseValue(d.getBaseValue());
            e.setWeight(d.getWeight());
            e.setScore(d.getScore());
            out.add(e);
        }
        EasyExcel.write(response.getOutputStream(), KpiScoreDetailExportRow.class)
                .sheet("KPI明细").doWrite(out);
    }

    /**
     * KPI 计算结果详情页"指标"下拉：仅含该 KPI 方案配置的指标（编号 + 名称）.
     *
     * @param schemeCode KPI 方案编码
     * @return 指标下拉项
     */
    @GetMapping("/scheme-metrics")
    @Operation(summary = "KPI方案的指标下拉项")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.READ)
    public ResponseWrapper<java.util.List<MetricOptionDTO>> schemeMetrics(
            @RequestParam("schemeCode") String schemeCode) {
        return ResponseWrapper.success(kpiScoreCalcService.listSchemeMetrics(schemeCode));
    }
}
