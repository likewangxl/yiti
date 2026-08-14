package com.bank.branch.platform.performance.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Quartz 触发参数读取工具：从合并后的 {@code JobDataMap} 解析手动触发携带的参数.
 *
 * <p>手动触发（{@code JobService.triggerJob}）会把 {@code dataDate / triggerType / operatorEmpId}
 * 放入 Trigger 的 JobDataMap；cron 自动触发则没有这些键。计算类 Job 用本工具统一解析：
 * <ul>
 *   <li>{@link #dataDate(JobExecutionContext)}：有合法 yyyy-MM-dd 则用之，否则按上海时区回退「昨日」（与历史自动触发一致）；</li>
 *   <li>{@link #triggerType(JobExecutionContext)}：MANUAL / AUTO（缺省 AUTO）；</li>
 *   <li>{@link #operatorEmpId(JobExecutionContext)}：手动触发人工号（自动触发为 null）。</li>
 * </ul>
 */
@Slf4j
public final class JobTriggerParams {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private JobTriggerParams() {
    }

    /**
     * 解析数据日期：JobDataMap 含合法 {@code dataDate}(yyyy-MM-dd) 则用之，否则按上海时区回退昨日.
     *
     * @param context Quartz 执行上下文
     * @return 数据日期（永不为 null）
     */
    public static LocalDate dataDate(JobExecutionContext context) {
        String raw = str(context, "dataDate");
        if (raw != null && !raw.isBlank()) {
            try {
                return LocalDate.parse(raw.trim());
            } catch (Exception e) {
                log.warn("[JobTriggerParams] dataDate 解析失败，回退昨日：raw={}", raw);
            }
        }
        return LocalDate.now(SHANGHAI).minusDays(1);
    }

    /**
     * 解析业绩分配日期：JobDataMap 含合法 {@code allocDate}(yyyy-MM-dd) 则用之，否则返回 null.
     *
     * <p>与 {@link #dataDate(JobExecutionContext)} 不同——allocDate 是<em>可选</em>入参，缺失/非法
     * 时返回 {@code null}（而非回退昨日），由下游计算引擎兜底为 dataDate（即 T-1）。
     *
     * @param context Quartz 执行上下文
     * @return 业绩分配日期；缺失或解析失败返回 null
     */
    public static LocalDate allocDate(JobExecutionContext context) {
        String raw = str(context, "allocDate");
        if (raw != null && !raw.isBlank()) {
            try {
                return LocalDate.parse(raw.trim());
            } catch (Exception e) {
                log.warn("[JobTriggerParams] allocDate 解析失败，返回 null（兜底 dataDate）：raw={}", raw);
            }
        }
        return null;
    }

    /** 触发方式：MANUAL（手动）/ AUTO（缺省，自动/定时）. */
    public static String triggerType(JobExecutionContext context) {
        String t = str(context, "triggerType");
        return (t == null || t.isBlank()) ? "AUTO" : t;
    }

    /** 触发人工号（手动触发携带；自动触发为 null）. */
    public static String operatorEmpId(JobExecutionContext context) {
        return str(context, "operatorEmpId");
    }

    private static String str(JobExecutionContext context, String key) {
        if (context == null) {
            return null;
        }
        Object v = context.getMergedJobDataMap().get(key);
        return v == null ? null : String.valueOf(v);
    }
}
