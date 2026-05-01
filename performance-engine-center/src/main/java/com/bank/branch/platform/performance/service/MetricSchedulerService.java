package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.governance.api.dto.RegisterJobCmd;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.job.quartz.MetricExecuteQuartzJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 指标调度同步服务（V1.7）.
 *
 * <p>把 PerfMetricDef 的 CRUD 状态变更同步成 Quartz 调度状态.
 * 启动期 + CRUD afterCommit 两类入口.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricSchedulerService {

    private final JobApi jobApi;
    private final MetricDefService metricDefService;
    private final MetricCronResolver cronResolver;

    /**
     * 应用启动后同步所有可调度指标到 Quartz.
     *
     * <p>逐个 register，单条异常不阻断整体同步.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        List<PerfMetricDef> metrics = metricDefService.listSchedulable();
        int success = 0, failed = 0;
        for (PerfMetricDef m : metrics) {
            try {
                register(m);
                success++;
            } catch (Exception e) {
                log.error("[MetricScheduler] sync 失败 metricCode={}", m.getMetricCode(), e);
                failed++;
            }
        }
        log.info("[MetricScheduler] 启动同步完成 success={} failed={}", success, failed);
    }

    /**
     * 注册（或覆盖）一个指标调度任务.
     *
     * <p>不满足调度条件或 EXPR/GROOVY 主体 SQL 为空时跳过.
     *
     * @param def 指标定义
     */
    public void register(PerfMetricDef def) {
        if (!isSchedulable(def)) {
            log.warn("[MetricScheduler] metric={} 不满足调度条件，跳过", def.getMetricCode());
            return;
        }
        // EXPR / GROOVY 类型必须有主体集合 SQL，否则执行期无法确定主体范围
        if (("EXPR".equalsIgnoreCase(def.getCalcLogicType())
                || "GROOVY".equalsIgnoreCase(def.getCalcLogicType()))
                && !StringUtils.hasText(def.getSubjectSql())) {
            log.warn("[MetricScheduler] metric={} EXPR/GROOVY 类型 subject_sql 为空，跳过注册",
                    def.getMetricCode());
            return;
        }
        RegisterJobCmd cmd = new RegisterJobCmd();
        cmd.setJobKey("PERF_METRIC_" + def.getMetricCode());
        cmd.setJobName("指标 " + def.getMetricCode() + " 自动调度");
        cmd.setCronExpr(cronResolver.resolve(def));
        cmd.setQuartzJobClass(MetricExecuteQuartzJob.class.getName());
        cmd.setJobData(Map.of("metricCode", def.getMetricCode()));
        cmd.setMisfirePolicy("FIRE_ONCE_NOW");
        cmd.setAllowManualTrigger(true);
        jobApi.registerJob(cmd);
    }

    /**
     * 注销指标调度任务（幂等）.
     *
     * @param metricCode 指标编码
     */
    public void unregister(String metricCode) {
        jobApi.unregisterJob("PERF_METRIC_" + metricCode);
    }

    /**
     * 判断指标是否满足自动调度条件.
     *
     * <p>必须同时满足：ACTIVE + AUTO + 未软删除 + 非 PROC/SUMMARY 逻辑类型.
     *
     * @param def 指标定义
     * @return true 表示可调度
     */
    public boolean isSchedulable(PerfMetricDef def) {
        if (def == null) return false;
        if (!"ACTIVE".equalsIgnoreCase(def.getStatus())) return false;
        if (!"AUTO".equalsIgnoreCase(def.getCalcMode())) return false;
        if (def.getDeleted() != null && def.getDeleted() == 1) return false;
        // PROC / SUMMARY 类型由外部存储过程或汇总链触发，不走通用调度
        if ("PROC".equalsIgnoreCase(def.getCalcLogicType())
                || "SUMMARY".equalsIgnoreCase(def.getCalcLogicType())) {
            return false;
        }
        return true;
    }
}
