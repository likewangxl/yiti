package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.service.MetricBatchCalcService;
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
 * 指标批量计算手动触发接口（测试/运维用）
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/metric-calc")
@RequiredArgsConstructor
@Tag(name = "指标批量计算", description = "手动触发指标批量计算（测试/运维）")
public class MetricBatchCalcController {

    private final MetricBatchCalcService metricBatchCalcService;

    /**
     * 手动触发指定级别的指标批量计算
     *
     * @param level    指标级别 1/2/3
     * @param dataDate 数据日期（yyyy-MM-dd），不传默认昨日
     */
    @PostMapping("/trigger")
    @Operation(summary = "手动触发指标批量计算")
    public ResponseWrapper<String> trigger(
            @RequestParam int level,
            @RequestParam(required = false) String dataDate) {
        LocalDate dt = dataDate != null && !dataDate.isEmpty()
                ? LocalDate.parse(dataDate)
                : LocalDate.now().minusDays(1);
        log.info("[MetricBatchCalcController.trigger] level={}, dataDate={}", level, dt);
        metricBatchCalcService.execute(level, dt);
        return ResponseWrapper.success("执行完成，请查看 PERF_METRIC_CALC_TASK 表");
    }
}
