package com.bank.branch.platform.governance.service;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * 仅供 {@link JobServiceSyncOnStartupTest} 使用的最简 Quartz Job NoOp 实现.
 *
 * <p>必须为 public 顶级类，使 {@code Class.forName(...)} 反射加载成功；execute 不做任何事，
 * 仅满足 {@link Job} 接口契约（Mockito Scheduler.scheduleJob 接收的 JobDetail 不会真正触发）.
 */
public class SyncTestNoOpJob implements Job {

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        // no-op: 单元测试场景，scheduleJob 仅做 mock 验证，不会真实触发
    }
}
