package com.bank.branch.platform.redengine.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务导出异步执行入口。
 *
 * <p>导出作业不能依赖提交请求线程的事务上下文；将入口单独放在 Spring bean 上，
 * 由容器代理开启独立事务，确保状态抢占、业务数据读取和失败状态回写在明确的事务边界内。
 * {@link Lazy} 只用于打断 worker 与导出服务之间的构造期循环依赖，不改变运行期代理调用。</p>
 */
@Component
public class ReTaskExportWorker {

    /** 导出服务使用延迟代理，避免服务与 worker 的构造期循环依赖。 */
    private final ReTaskExportServiceImpl exportService;

    public ReTaskExportWorker(@Lazy ReTaskExportServiceImpl exportService) {
        this.exportService = exportService;
    }

    /**
     * 执行一个导出作业。
     *
     * @param exportId 导出作业 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void execute(String exportId) {
        exportService.executeExportNow(exportId);
    }
}
