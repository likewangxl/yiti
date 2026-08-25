package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.dto.JobTriggerRespDTO;
import com.bank.branch.platform.performance.controller.dto.MetricLevelTriggerReqDTO;
import com.bank.branch.platform.performance.service.MetricBatchCalcService;
import com.bank.branch.platform.performance.service.MetricLevelTriggerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
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
 * <p><b>鉴权链路已完整（2026-07-19 收口）</b>：该 URL 曾于 2026-05-27（提交 326bba98）
 * 加入 {@code auth-permission-center} 的 {@code AuthenticationFilter.WHITELIST} 与
 * {@code WebMvcAuthConfig.excludePathPatterns} 作为调试便捷通道，期间匿名即可触发批量计算、
 * 本注解不生效。经全仓排查确认无裸调依赖方后两处白名单已摘除，当前走
 * Session → {@code AuthorizationInterceptor}（{@code P_PERF_CALC_TRIG}）→ {@code @BizAuth}
 * 全链路，由 bootstrap 的 {@code PerfTriggerAuthWhitelistRemovalIT} 固定回归
 * （匿名 401 / 登录后可达）。运维如需手工触发，须先登录携带会话，不能再裸 curl。
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/metric-calc")
@RequiredArgsConstructor
@Tag(name = "指标批量计算", description = "手动触发指标批量计算（测试/运维）")
public class MetricBatchCalcController {

    private final MetricBatchCalcService metricBatchCalcService;
    private final MetricLevelTriggerService metricLevelTriggerService;
    private final CurrentUserApi currentUserApi;

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

    /**
     * 提交按级别指标重算任务。
     *
     * <p>该入口只提交治理中心 Quartz 触发，真正的指标计算由对应 Level Job 异步执行；请求线程不得直接调用
     * {@link MetricBatchCalcService#execute(int, LocalDate, LocalDate, String)}。</p>
     */
    @PostMapping("/level-trigger")
    @Operation(summary = "按级别提交指标重算任务")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXECUTE)
    @AuditLog(action = "PERF_METRIC_LEVEL_RECALC", resourceType = "PERF_METRIC_CALC_TASK", reasonRequired = true)
    public ResponseWrapper<JobTriggerRespDTO> triggerLevel(@Valid @RequestBody MetricLevelTriggerReqDTO req) {
        String operatorEmpId = currentUserApi.getCurrentEmpId();
        JobTriggerRespDTO result = metricLevelTriggerService.trigger(req, operatorEmpId);
        return ResponseWrapper.success(result);
    }
}
