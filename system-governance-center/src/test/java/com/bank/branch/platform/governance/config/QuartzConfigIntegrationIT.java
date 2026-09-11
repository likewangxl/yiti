package com.bank.branch.platform.governance.config;

import com.bank.branch.platform.governance.listener.JobExecutionLogger;
import com.bank.branch.platform.governance.mapper.JobConfMapper;
import com.bank.branch.platform.governance.mapper.JobRunLogMapper;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QuartzConfig 集成测试（V1.6 quartz 整合 P1.6）.
 *
 * <p>验证 SchedulerFactoryBean 装配后 Scheduler bean 可注入并启动；
 * 测试用 RAMJobStore（job-store-type=memory），不依赖 QRTZ_* 表与真实 DataSource。
 *
 * <p>策略：
 * <ul>
 *   <li>使用最小 Test SpringBootApplication（QuartzConfigIntegrationITApp）作为 @SpringBootTest 根，
 *       仅扫描必需 3 个类（QuartzConfig + AutowiringSpringBeanJobFactory + JobExecutionLogger），
 *       让 Spring Boot 的 QuartzAutoConfiguration 在 spring.quartz.* 属性下生效</li>
 *   <li>JobExecutionLogger 依赖的 JobConfMapper / JobRunLogMapper 用 @MockBean 注入</li>
 *   <li>@TestPropertySource 强制 job-store-type=memory，覆盖业务 application.yml 的 jdbc 默认</li>
 * </ul>
 */
@SpringBootTest(classes = QuartzConfigIntegrationIT.QuartzConfigIntegrationITApp.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.quartz.job-store-type=memory",
        // 测试场景立即启动（governance 模块测试不读 bootstrap/application.yml，无 startup-delay 默认即 0，
        // 但显式声明便于阅读）
        "spring.quartz.startup-delay=0s",
        // 跳过 src/test/resources/schema.sql / data.sql 初始化，本测试仅验证 Quartz 装配，无需 governance 业务表
        "spring.sql.init.mode=never",
        // 模拟主配置继承到 branch-dashboard 的 JDBC 专属 jobStore 属性；RAMJobStore 仍应正常启动。
        "spring.quartz.properties.org.quartz.scheduler.instanceName=governance-memory-test",
        "spring.quartz.properties.org.quartz.threadPool.threadCount=5",
        "spring.quartz.properties.org.quartz.threadPool.threadPriority=5",
        "spring.quartz.properties.org.quartz.jobStore.class=org.quartz.simpl.RAMJobStore",
        "spring.quartz.properties.org.quartz.jobStore.driverDelegateClass=org.quartz.impl.jdbcjobstore.StdJDBCDelegate",
        "spring.quartz.properties.org.quartz.jobStore.tablePrefix=QRTZ_",
        "spring.quartz.properties.org.quartz.jobStore.isClustered=true",
        "spring.quartz.properties.org.quartz.jobStore.clusterCheckinInterval=20000",
        "spring.quartz.properties.org.quartz.jobStore.misfireThreshold=60000"
})
class QuartzConfigIntegrationIT {

    @Autowired
    private Scheduler scheduler;

    @MockBean
    private JobConfMapper jobConfMapper;

    @MockBean
    private JobRunLogMapper jobRunLogMapper;

    @Test
    void scheduler_isStartedAndConfigured() throws SchedulerException {
        assertThat(scheduler).isNotNull();
        assertThat(scheduler.isStarted()).isTrue();
        assertThat(scheduler.getMetaData().getJobStoreClass().getSimpleName())
                .isEqualTo("RAMJobStore");
        assertThat(scheduler.getMetaData().getThreadPoolSize()).isEqualTo(5);
    }

    /**
     * 测试专用 SpringBoot 启动类，仅扫描当前测试内部类所在包，避免拉入 governance 全模块的
     * WebMvcAuthConfig / GovCacheConfig / MinioConfig 等无关 bean.
     *
     * <p>装配链：
     * <ul>
     *   <li>@SpringBootApplication(scanBasePackages="com.bank.branch.platform.governance.config.itscope.notexist")
     *       —— 用一个不存在的包关闭 component scan</li>
     *   <li>@Import 显式装入测试需要的 3 个类（JobExecutionLogger 在外包，必须 @Import 而非 scan）</li>
     *   <li>JobExecutionLogger 依赖的 JobConfMapper / JobRunLogMapper 由测试 @MockBean 提供</li>
     * </ul>
     */
    @SpringBootApplication(scanBasePackages = "com.bank.branch.platform.governance.config.itscope.notexist")
    @Import({QuartzConfig.class, JobExecutionLogger.class})
    static class QuartzConfigIntegrationITApp {
    }
}
