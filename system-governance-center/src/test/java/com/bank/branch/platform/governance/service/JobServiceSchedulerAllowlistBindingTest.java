package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.governance.config.QuartzConfig;
import com.bank.branch.platform.governance.entity.SysJobConf;
import com.bank.branch.platform.governance.listener.JobExecutionLogger;
import com.bank.branch.platform.governance.mapper.JobConfMapper;
import com.bank.branch.platform.governance.mapper.JobRunLogMapper;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Verifies YAML/indexed binding of governance.scheduler.allowed-job-keys with RAM Quartz. */
@SpringBootTest(classes = JobServiceSchedulerAllowlistBindingTest.TestApp.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.quartz.job-store-type=memory",
        "spring.quartz.startup-delay=0s",
        "spring.sql.init.mode=never",
        "governance.scheduler.allowed-job-keys[0]=BRANCH_DASHBOARD_BATCH"
})
class JobServiceSchedulerAllowlistBindingTest {

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private JobService jobService;

    @MockBean
    private JobConfMapper jobConfMapper;

    @MockBean
    private JobRunLogMapper jobRunLogMapper;

    @Test
    void boundAllowlist_fencesStartupSyncOnRamQuartz() throws Exception {
        SysJobConf allowed = job("J_ALLOWED", "BRANCH_DASHBOARD_BATCH");
        SysJobConf blocked = job("J_BLOCKED", "RED_ENGINE_TASK_WINDOW");
        when(jobConfMapper.selectByStatus("ACTIVE")).thenReturn(List.of(allowed, blocked));

        jobService.syncJobsOnStartup();

        assertThat(scheduler.checkExists(JobKey.jobKey("BRANCH_DASHBOARD_BATCH", "DEFAULT"))).isTrue();
        assertThat(scheduler.checkExists(JobKey.jobKey("RED_ENGINE_TASK_WINDOW", "DEFAULT"))).isFalse();
        scheduler.deleteJob(JobKey.jobKey("BRANCH_DASHBOARD_BATCH", "DEFAULT"));
    }

    private SysJobConf job(String id, String key) {
        SysJobConf conf = new SysJobConf();
        conf.setId(id);
        conf.setJobKey(key);
        conf.setJobName(key);
        conf.setCronExpr("0 0 2 * * ?");
        conf.setQuartzJobClass(SyncTestNoOpJob.class.getName());
        conf.setMisfirePolicy("FIRE_ONCE_NOW");
        conf.setStatus("ACTIVE");
        conf.setAllowManualTrigger(1);
        conf.setCreatedTime(LocalDateTime.now());
        conf.setUpdatedTime(LocalDateTime.now());
        return conf;
    }

    @SpringBootApplication(scanBasePackages = "com.bank.branch.platform.governance.service.itscope.notexist")
    @Import({QuartzConfig.class, JobExecutionLogger.class, JobService.class})
    static class TestApp {
    }
}
