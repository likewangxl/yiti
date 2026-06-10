package com.bank.branch.platform.customer.job.quartz;

import com.bank.branch.platform.customer.service.CustMasterSyncService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * 客户信息同步 Quartz 定时任务（job_key=CUST_INFO_SYNC）。
 * <p>
 * 由 {@code sys_job_conf.cron_expr} 控制触发（默认每日凌晨），调用 {@link CustMasterSyncService#syncYesterday()}：
 * 把外部客户统计表 {@code XAN_M98_CUST_STAT_SHOW3} 中昨日新出现、客户主档尚不存在的客户同步进 {@code CUST_MASTER}。
 * </p>
 * <p>
 * 与 perf 的 Level1/2/3 / KPI 计算 job 同构：不加 {@code @Component}、用 {@code @Autowired} 字段注入，
 * Quartz 经 {@code AutowiringSpringBeanJobFactory} 反射无参构造后 autowireBean。
 * 多实例集群防重由 Quartz QRTZ_LOCKS 行锁兜底；执行日志由 governance 全局 JobListener 统一记录。
 * </p>
 */
@Slf4j
public class CustMasterSyncJob extends QuartzJobBean {

    @Autowired
    private CustMasterSyncService custMasterSyncService;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        log.info(">>>>>>>>>> 【客户信息同步定时任务】触发执行 <<<<<<<<<<");
        try {
            int inserted = custMasterSyncService.syncYesterday();
            log.info(">>>>>>>>>> 【客户信息同步定时任务】执行完成，新增客户主档 {} 条 <<<<<<<<<<", inserted);
        } catch (Exception e) {
            // 顶层兜底：业务异常不冒泡到 Quartz scheduler（避免 misfire 重试风暴），失败状态由 JobExecutionLogger 记录
            log.error(">>>>>>>>>> 【客户信息同步定时任务】执行异常: {} <<<<<<<<<<", e.getMessage(), e);
        }
    }
}
