package com.bank.branch.platform.performance.eval.job;

import com.bank.branch.platform.performance.eval.service.EvalAssignAdminService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 导入批次过期关闭 Quartz Job（job_key=EVAL_ASSIGN_BATCH_EXPIRE）。
 *
 * <p>周期扫描 {@code EVAL_ASSIGN_BATCH} 中 status=0(进行中) 且 deadline 已过的批次，统一置
 * status=1(已结束)——覆盖评价任务导入(EVAL)与奖励分配(REWARD)两类导入批次，使管理端列表状态与
 * 用户端「过期不可提交」保持一致。</p>
 *
 * <p>不加 {@code @Component}！由 governance {@code JobService.syncJobsOnStartup} 按 SYS_JOB_CONF
 * 行（quartz_job_class 指向本类）注册 JobDetail+Trigger，Quartz 反射建实例 +
 * {@code AutowiringSpringBeanJobFactory} 注入 Spring bean。{@link DisallowConcurrentExecution}
 * 防同一 JobDetail 并发/多节点重叠（业务幂等，重叠无意义）。</p>
 */
@Slf4j
@DisallowConcurrentExecution
public class EvalAssignBatchExpireJob implements Job {

    @Autowired
    private EvalAssignAdminService evalAssignAdminService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            int closed = evalAssignAdminService.closeExpiredBatches();
            log.info("[EvalAssignBatchExpireJob] 扫描完成，关闭过期批次 {} 个", closed);
        } catch (Exception e) {
            log.error("[EvalAssignBatchExpireJob] 执行异常", e);
            throw new JobExecutionException(e, false);
        }
    }
}
