package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.service.KpiScoreCalcService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

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

    /**
     * 触发 KPI 分值计算.
     *
     * @param dataDate   数据日期（yyyy-MM-dd，必填）
     * @param schemeCode KPI 方案编码（可空，空=全部 ACTIVE 方案）
     * @return 任务流水 ID
     */
    @PostMapping("/calc")
    @Operation(summary = "计算/重算 KPI 得分")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXECUTE)
    @AuditLog(action = "EXECUTE", resourceType = "KPI_SCORE")
    public ResponseWrapper<String> calc(
            @RequestParam("dataDate") String dataDate,
            @RequestParam(value = "schemeCode", required = false) String schemeCode) {
        LocalDate dt = LocalDate.parse(dataDate);
        log.info("[KpiScoreCalcController.calc] dataDate={}, schemeCode={}", dt, schemeCode);
        String taskId = kpiScoreCalcService.calculate(dt, schemeCode);
        return ResponseWrapper.success(taskId);
    }
}
