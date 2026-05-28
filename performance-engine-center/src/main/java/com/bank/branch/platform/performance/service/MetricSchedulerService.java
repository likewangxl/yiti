package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.governance.api.dto.RegisterJobCmd;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.job.quartz.MetricExecuteQuartzJob;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 *
 * <p>启动同步直接使用 {@link PerfMetricDefMapper} 查询，
 * 避免与 MetricDefService 的循环依赖（MetricDefService 持有 MetricSchedulerService 引用）.
 */
@Slf4j
@Service
public class MetricSchedulerService {

    private final JobApi jobApi;
    private final PerfMetricDefMapper perfMetricDefMapper;
    private final MetricCronResolver cronResolver;

    /** 启动期全量同步开关，false 时跳过 selectSchedulable + registerJob 循环（运维侧关停补回风暴）. */
    @Value("${perf.scheduler.startup-sync.enabled:true}")
    private boolean startupSyncEnabled;

    public MetricSchedulerService(JobApi jobApi,
                                  PerfMetricDefMapper perfMetricDefMapper,
                                  MetricCronResolver cronResolver) {
        this.jobApi = jobApi;
        this.perfMetricDefMapper = perfMetricDefMapper;
        this.cronResolver = cronResolver;
    }

    /**
     * 应用启动后同步所有可调度指标到 Quartz.
     *
     * <p>逐个 register，单条异常不阻断整体同步.
     * <p>{@code perf.scheduler.startup-sync.enabled=false} 时整个同步过程跳过.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        if (!startupSyncEnabled) {
            log.warn("[MetricScheduler] 启动同步已通过 perf.scheduler.startup-sync.enabled=false 关停，跳过");
            return;
        }
        List<PerfMetricDef> metrics = perfMetricDefMapper.selectSchedulable();
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
     * <p>不满足调度条件时跳过（含 EXPR/GROOVY subjectSql 为空的情况，由 isSchedulable 统一判定）.
     *
     * @param def 指标定义
     */
    public void register(PerfMetricDef def) {
        if (!isSchedulable(def)) {
            log.warn("[MetricScheduler] metric={} 不满足调度条件，跳过",
                    def == null ? "null" : def.getMetricCode());
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
     * V1.7：EXPR/GROOVY 类型 subject_sql 必填，空时视为不可调度（消除 register 双重判定）.
     *
     * @param def 指标定义
     * @return true 表示可调度
     */
    public boolean isSchedulable(PerfMetricDef def) {
        if (def == null) return false;
        if (!"ACTIVE".equalsIgnoreCase(def.getStatus())) return false;
        if (!"AUTO".equalsIgnoreCase(def.getCalcMode())) return false;
        if (def.getDeleted() != null && def.getDeleted() == 1) return false;
        // V1.9：维度无关型指标（baseDim=null）无 slot、无宽表归属，不进入自动调度
        if (def.getBaseDim() == null || def.getBaseDim().isBlank()) return false;
        // PROC / SUMMARY 类型由外部存储过程或汇总链触发，不走通用调度
        if ("PROC".equalsIgnoreCase(def.getCalcLogicType())
                || "SUMMARY".equalsIgnoreCase(def.getCalcLogicType())) {
            return false;
        }
        // EXPR / GROOVY 类型必须有主体集合 SQL，否则执行期无法确定主体范围
        if (("EXPR".equalsIgnoreCase(def.getCalcLogicType())
                || "GROOVY".equalsIgnoreCase(def.getCalcLogicType()))
                && !StringUtils.hasText(def.getSubjectSql())) {
            return false;
        }
        return true;
    }
}
