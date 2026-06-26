package com.bank.branch.platform.portal.job.quartz;

import com.bank.branch.platform.portal.service.GuaranteeSyncService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * 担保信息每日同步 Quartz 定时任务（job_key=GUARANTEE_INFO_SYNC）。
 * <p>
 * 由 {@code sys_job_conf.cron_expr} 控制触发（默认每日 06:30），调用 {@link GuaranteeSyncService#processing()}：
 * 把前一日 {@code clms_ed_credit_info} 担保类授信额度同步进 {@code zh_guarantee_info}。
 * </p>
 * <p>
 * 与 customer 的 CustMasterSyncJob / perf 计算 job 同构：不加 {@code @Component}、用 {@code @Autowired}
 * 字段注入，Quartz 经 {@code AutowiringSpringBeanJobFactory} 反射无参构造后 autowireBean。
 * 多实例集群防重由 Quartz QRTZ_LOCKS 行锁兜底；执行日志由 governance 全局 JobListener 统一记录。
 * </p>
 */
@Slf4j
public class GuaranteeSyncJob extends QuartzJobBean {

    @Autowired
    private GuaranteeSyncService guaranteeSyncService;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        log.info(">>>>>>>>>> 【担保信息同步定时任务】触发执行 <<<<<<<<<<");
        try {
            guaranteeSyncService.processing();
            log.info(">>>>>>>>>> 【担保信息同步定时任务】执行完成 <<<<<<<<<<");
        } catch (Exception e) {
            // 顶层兜底：业务异常不冒泡到 Quartz scheduler（避免 misfire 重试风暴），失败状态由 JobExecutionLogger 记录
            log.error(">>>>>>>>>> 【担保信息同步定时任务】执行异常: {} <<<<<<<<<<", e.getMessage(), e);
        }
    }
}
