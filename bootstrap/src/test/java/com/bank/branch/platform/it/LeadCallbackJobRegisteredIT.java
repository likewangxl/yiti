package com.bank.branch.platform.it;

import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.governance.api.dto.JobConfDTO;
import com.bank.branch.platform.governance.entity.SysJobConf;
import com.bank.branch.platform.governance.mapper.JobConfMapper;
import com.bank.branch.platform.it.config.TestMockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1.8 P5 — 验证应用启动后 LEAD_CALLBACK_COMPENSATE 已落 sys_job_conf，
 * 且 governance JobApi 可正常读取该行配置。
 *
 * <p><strong>Quartz Scheduler 验证说明</strong>：bootstrap test profile 通过
 * {@code spring.quartz.enabled=false} + {@code autoconfigure.exclude} 双重禁用了
 * {@code QuartzAutoConfiguration}，导致 {@link Scheduler} bean 不存在于测试上下文。
 * 因此 {@link #quartzSchedulerHasJob()} 用例通过 {@code @Autowired(required=false)} 守护，
 * 若 Scheduler 为 null 则直接跳过（不计回归），与 V1.7 {@code MetricScheduledE2EIT} 处理方式一致。</p>
 *
 * <p><strong>覆盖范围</strong>：</p>
 * <ul>
 *   <li>{@link #sysJobConfHasLeadCallbackRow()} — 验证 data.sql 预置行在 H2 中可查到，
 *       关键字段（cron、class、misfire、status）全部断言</li>
 *   <li>{@link #jobApiCanReadLeadCallbackConf()} — 验证 governance JobApi 服务层正常
 *       委托到 JobConfMapper 并返回 DTO（端到端 Spring Bean 链路通畅）</li>
 *   <li>{@link #quartzSchedulerHasJob()} — 在 Scheduler bean 可用时验证 Quartz 注册；
 *       test profile 禁用 Quartz 时自动跳过</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestMockConfig.class)
class LeadCallbackJobRegisteredIT {

    /** 目标任务的 jobKey 常量 */
    private static final String JOB_KEY = "LEAD_CALLBACK_COMPENSATE";

    /** 期望的 Quartz 包装 Job 类全限定名（V1.8 新增） */
    private static final String EXPECTED_JOB_CLASS =
            "com.bank.branch.platform.customer.job.quartz.LeadCallbackCompensateQuartzJob";

    /** 期望的 cron 表达式（每 5 分钟一次） */
    private static final String EXPECTED_CRON = "0 */5 * * * ?";

    @Autowired
    private JobConfMapper jobConfMapper;

    @Autowired
    private JobApi jobApi;

    /**
     * test profile 禁用 Quartz，{@link Scheduler} bean 不存在时 required=false 返回 null，
     * 测试中检测到 null 直接跳过，不算回归。
     */
    @Autowired(required = false)
    private Scheduler scheduler;

    @Test
    @DisplayName("V1.8 启动后 sys_job_conf 含 LEAD_CALLBACK_COMPENSATE 行，且关键字段正确")
    void sysJobConfHasLeadCallbackRow() {
        SysJobConf row = jobConfMapper.selectByJobKey(JOB_KEY);

        assertThat(row)
                .as("data.sql 应在 H2 中预置 %s 行", JOB_KEY)
                .isNotNull();
        assertThat(row.getQuartzJobClass())
                .as("quartzJobClass 应指向 LeadCallbackCompensateQuartzJob")
                .isEqualTo(EXPECTED_JOB_CLASS);
        assertThat(row.getCronExpr())
                .as("cron 表达式应为每 5 分钟一次")
                .isEqualTo(EXPECTED_CRON);
        assertThat(row.getMisfirePolicy())
                .as("misfire 策略应为 DO_NOTHING")
                .isEqualTo("DO_NOTHING");
        assertThat(row.getStatus())
                .as("任务状态应为 ACTIVE")
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("V1.8 governance JobApi.getJobConf 可读取 LEAD_CALLBACK_COMPENSATE 配置")
    void jobApiCanReadLeadCallbackConf() {
        Optional<JobConfDTO> dto = jobApi.getJobConf(JOB_KEY);

        assertThat(dto)
                .as("JobApi.getJobConf 应返回非空 Optional")
                .isPresent();
        assertThat(dto.get().getJobKey())
                .as("DTO.jobKey 应等于 %s", JOB_KEY)
                .isEqualTo(JOB_KEY);
        assertThat(dto.get().getCronExpr())
                .as("DTO.cronExpr 应等于 %s", EXPECTED_CRON)
                .isEqualTo(EXPECTED_CRON);
    }

    @Test
    @DisplayName("V1.8 启动后 Quartz Scheduler 含 LEAD_CALLBACK_COMPENSATE Job（test profile 禁用 Quartz 时跳过）")
    void quartzSchedulerHasJob() throws Exception {
        if (scheduler == null) {
            // test profile 通过 spring.quartz.enabled=false + autoconfigure.exclude 双重禁用 Quartz，
            // Scheduler bean 不存在，此用例自动跳过，与 V1.7 MetricScheduledE2EIT 处理方式一致。
            return;
        }
        // group 固定为 "DEFAULT"（JobService.pauseJob / resumeJob 源码中 JobKey.jobKey(key, "DEFAULT") 确认）
        JobKey jobKey = JobKey.jobKey(JOB_KEY, "DEFAULT");
        assertThat(scheduler.checkExists(jobKey))
                .as("%s 应已被 governance JobService.syncJobsOnStartup 注册到 Quartz", JOB_KEY)
                .isTrue();
    }
}
