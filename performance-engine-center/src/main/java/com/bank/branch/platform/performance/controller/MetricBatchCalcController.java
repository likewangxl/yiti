package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
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
 *
 * <p><b>2026-07-19 鉴权修复</b>：本接口此前未标注 {@code @BizAuth}，且
 * {@code BizAuthConsistencyArchTest} 只校验"若声明则 bizType 必须单档"、不要求
 * "必须声明"，导致这一缺口长期未被架构测试拦截（详见新增的
 * {@code BizAuthRequiredArchTest}）。现补齐 {@code @BizAuth(bizType = PERF_CONFIG,
 * action = EXECUTE)}，与同包 {@code MetricDefController.execute}/{@code batchExecute}
 * 的"手动触发执行"语义保持一致。
 *
 * <p><b>⚠️ 遗留风险（本次修复未覆盖，需另行决策）</b>：{@code /api/perf/metric-calc/trigger}
 * 目前仍同时登记在 {@code auth-permission-center} 的
 * {@code AuthenticationFilter.WHITELIST} 与 {@code WebMvcAuthConfig} 的
 * {@code excludePathPatterns} 中（见 2026-05-27 提交 326bba98，注释"调试/运维触发...
 * 跳过 Session 校验与 RBAC 注册"）——这两处白名单会让请求在到达
 * {@code AuthorizationInterceptor} 之前就被放行，本次新增的 {@code @BizAuth} 注解
 * 因此暂不产生运行时鉴权效果（一旦白名单收紧，注解会立即生效）。是否移除这两条白名单
 * 需要产品/运维侧确认该"调试/运维便捷通道"是否还有依赖方，不在本次修复范围内。
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
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXECUTE)
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
