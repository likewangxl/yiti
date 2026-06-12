package com.bank.branch.platform.governance.job;

import com.bank.branch.platform.governance.service.SpringSessionCleanupService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spring Session 孤儿属性清理 Quartz 包装类。
 *
 * <p>不加 @Component！Quartz 通过反射 newInstance() 创建本对象 →
 * AutowiringSpringBeanJobFactory 完成 @Autowired 注入（见 governance QuartzConfig）。
 * 集群环境只在一个节点执行一次（QRTZ 行锁），避免多实例同时扫库加剧锁竞争。</p>
 */
@Slf4j
public class SpringSessionCleanupQuartzJob implements Job {

    @Autowired
    private SpringSessionCleanupService springSessionCleanupService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            springSessionCleanupService.cleanOrphanAttributes();
        } catch (Exception e) {
            log.error("[SpringSessionCleanupQuartzJob] 执行异常", e);
            throw new JobExecutionException(e, false);
        }
    }
}
