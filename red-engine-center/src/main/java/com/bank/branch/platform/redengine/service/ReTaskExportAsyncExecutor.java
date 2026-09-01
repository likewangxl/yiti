package com.bank.branch.platform.redengine.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * 任务导出异步执行器适配。
 *
 * <p>优先复用平台提供的 {@link TaskExecutor}；测试或最小模块上下文没有该 Bean 时退回
 * JDK 公共执行器。这里不创建 Quartz、@Scheduled 或模块级持久化调度器。</p>
 */
@Component
public class ReTaskExportAsyncExecutor {

    private final Executor delegate;

    @Autowired
    public ReTaskExportAsyncExecutor(ObjectProvider<TaskExecutor> taskExecutors) {
        this.delegate = taskExecutors.orderedStream()
                .map(taskExecutor -> (Executor) taskExecutor)
                .findFirst()
                .orElse(ForkJoinPool.commonPool());
    }

    ReTaskExportAsyncExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    /** 提交一个不阻塞 HTTP 请求的导出任务。 */
    public void submit(Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("异步任务不能为空");
        }
        delegate.execute(task);
    }
}
