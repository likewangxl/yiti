package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 指标 cron 表达式推导器（V1.7）.
 *
 * <p>规则：cronExpr 非空时直接返回；为空时按 calcFreq 推导默认（统一凌晨 2 点）.
 * 用法：MetricSchedulerService.register 注册前调 resolve 得到最终 cron.
 */
@Component
public class MetricCronResolver {

    /**
     * 推导指标的最终 cron 表达式.
     *
     * @param def 指标定义
     * @return 合法 cron 表达式
     * @throws PerfException PERF-40021 calc_freq 非法
     */
    public String resolve(PerfMetricDef def) {
        // cronExpr 显式配置时优先使用，无需推导
        if (StringUtils.hasText(def.getCronExpr())) {
            return def.getCronExpr();
        }
        // 按 calcFreq 推导默认 cron，统一凌晨 2 点触发
        String freq = def.getCalcFreq() == null ? "" : def.getCalcFreq().toUpperCase();
        return switch (freq) {
            case "DAY"     -> "0 0 2 * * ?";
            case "WEEK"    -> "0 0 2 ? * MON";
            case "MONTH"   -> "0 0 2 1 * ?";
            case "QUARTER" -> "0 0 2 1 1,4,7,10 ?";
            case "YEAR"    -> "0 0 2 1 1 ?";
            default -> throw new PerfException(PerfErrorCode.METRIC_CALC_FREQ_INVALID, freq);
        };
    }
}
