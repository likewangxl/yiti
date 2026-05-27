package com.bank.branch.platform.performance.eval.job;

import com.bank.branch.platform.performance.eval.entity.EvalTask;
import com.bank.branch.platform.performance.eval.mapper.EvalTaskMapper;
import com.bank.branch.platform.performance.eval.service.EvalTaskService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * 评价任务过期扫描 Quartz Job.
 * <p>Job Key: EVAL_TASK_EXPIRE
 * <p>扫描 status=0 且 end_time <= now 的评价任务，逐一调用 {@link EvalTaskService#closeTask} 关闭并计算得分。
 * <p>注意：此 Job 不加 {@code @Component}，由 {@code PerfQuartzConfig} 通过 {@code JobDetail} 注册，
 * Quartz 在执行时通过 Spring {@code AutowireCapableBeanFactory} 注入依赖。
 */
@Slf4j
public class EvalTaskExpireJob implements Job {

    @Autowired
    private EvalTaskMapper evalTaskMapper;

    @Autowired
    private EvalTaskService evalTaskService;

    /**
     * 扫描过期评价任务并逐一关闭.
     *
     * <p>单任务关闭失败只记录错误日志，不中断其余任务处理（逐一最大努力）。
     * 整体异常时包装为 {@link JobExecutionException} 向 Quartz 上报，{@code refireImmediately=false}
     * 避免立即重试导致雪崩。
     *
     * @param context Quartz 执行上下文
     * @throws JobExecutionException 整体扫描异常时抛出
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("[EvalTaskExpireJob] 开始扫描过期评价任务");
        try {
            List<EvalTask> expiredTasks = evalTaskMapper.selectExpiredActive();
            if (expiredTasks.isEmpty()) {
                log.info("[EvalTaskExpireJob] 无过期任务");
                return;
            }
            for (EvalTask task : expiredTasks) {
                try {
                    evalTaskService.closeTask(task.getTaskId());
                    log.info("[EvalTaskExpireJob] 已关闭任务 taskId={}", task.getTaskId());
                } catch (Exception e) {
                    log.error("[EvalTaskExpireJob] 关闭任务失败 taskId={}", task.getTaskId(), e);
                }
            }
            log.info("[EvalTaskExpireJob] 扫描完成，处理 {} 个过期任务", expiredTasks.size());
        } catch (Exception e) {
            log.error("[EvalTaskExpireJob] 执行异常", e);
            throw new JobExecutionException(e, false);
        }
    }
}
