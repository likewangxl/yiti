package com.bank.branch.platform.performance.job.quartz;

import com.bank.branch.platform.performance.job.JobTriggerParams;
import com.bank.branch.platform.performance.service.BranchDashboardBatchService;
import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchDTO;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

/**
 * 分行大屏批次 Quartz 包装类。
 *
 * <p>不加 {@code @Component}，由治理中心 Quartz 的 Spring JobFactory 注入依赖。JobDataMap
 * 未提供 dataDate 时把 null 交给批次服务，由服务选择最近完整业务日。</p>
 */
@Slf4j
@DisallowConcurrentExecution
public class BranchDashboardBatchQuartzJob implements Job {

    @Autowired
    private BranchDashboardBatchService batchService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            LocalDate dataDate = optionalDataDate(context == null ? null : context.getMergedJobDataMap());
            BranchDashboardBatchDTO result = batchService.runBatch(dataDate,
                    JobTriggerParams.triggerType(context),
                    JobTriggerParams.operatorEmpId(context));
            if (result == null || !"COMPLETE".equals(result.getStatus())) {
                String status = result == null ? "null" : result.getStatus();
                throw new JobExecutionException("分行大屏批次未完成，状态=" + status, false);
            }
        } catch (Exception ex) {
            log.error("[BranchDashboardBatchQuartzJob] 执行异常", ex);
            throw new JobExecutionException(ex, false);
        }
    }

    private LocalDate optionalDataDate(JobDataMap data) {
        if (data == null) {
            return null;
        }
        Object raw = data.get("dataDate");
        if (!StringUtils.hasText(raw == null ? null : String.valueOf(raw))) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(raw).trim());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("dataDate 格式必须为 yyyy-MM-dd", ex);
        }
    }
}
