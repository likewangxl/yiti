package com.bank.branch.platform.governance.config;

import com.bank.branch.platform.governance.listener.JobExecutionLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.quartz.JobStoreType;
import org.springframework.boot.autoconfigure.quartz.QuartzProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Quartz 调度引擎装配（V1.6 quartz 整合引入）.
 *
 * <p>本配置在 spring-boot-starter-quartz 提供的 {@code QuartzAutoConfiguration} 之上叠加项目定制：
 * <ol>
 *   <li>提供 {@link AutowiringSpringBeanJobFactory} bean 替代默认 SpringBeanJobFactory，
 *       让 Quartz Job 内部可 @Autowired Spring Bean</li>
 *   <li>注册 {@link SchedulerFactoryBeanCustomizer}：
 *       <ul>
 *         <li>设置 {@link AutowiringSpringBeanJobFactory} 为 JobFactory（覆盖默认）</li>
 *         <li>注册 {@link JobExecutionLogger} 为全局 JobListener，自动写 sys_job_run_log</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>共享主 DataSource、JdbcJobStore 持久化、isClustered、threadCount、startupDelay 等
 * 由 Spring Boot 的 {@code spring.quartz.*} 配置 + {@code QuartzAutoConfiguration.JdbcStoreTypeConfiguration}
 * 自动接管（spec 决策 #3=A，与 sys_job_conf 同库 onepl）。
 *
 * <p>开关: spring.quartz.enabled=true（默认 true，缺省即启用，与 spring-boot-starter-quartz 一致）.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spring.quartz", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class QuartzConfig {

    private static final String JOB_STORE_PREFIX = "org.quartz.jobStore.";

    /**
     * RAMJobStore 支持的 jobStore 属性。其余 jobStore 属性属于 JDBC/持久化实现，
     * 继承到内存调度配置时必须剔除，否则 Quartz 会尝试调用 RAMJobStore 不存在的 setter。
     */
    private static final Set<String> MEMORY_JOB_STORE_PROPERTIES = Set.of(
            JOB_STORE_PREFIX + "class",
            JOB_STORE_PREFIX + "misfireThreshold",
            JOB_STORE_PREFIX + "instanceId",
            JOB_STORE_PREFIX + "instanceName",
            JOB_STORE_PREFIX + "threadPoolSize"
    );

    /**
     * AutowiringSpringBeanJobFactory：Quartz Job 实例化时通过 Spring 完成 @Autowired 字段注入.
     *
     * @param ctx Spring 应用上下文
     * @return AutowiringSpringBeanJobFactory bean
     */
    @Bean
    public AutowiringSpringBeanJobFactory springBeanJobFactory(ApplicationContext ctx) {
        AutowiringSpringBeanJobFactory factory = new AutowiringSpringBeanJobFactory();
        factory.setApplicationContext(ctx);
        return factory;
    }

    /**
     * SchedulerFactoryBeanCustomizer：在 Spring Boot QuartzAutoConfiguration 的基础上叠加：
     * <ul>
     *   <li>JobFactory 替换为 AutowiringSpringBeanJobFactory（支持 Job 内部 @Autowired）</li>
     *   <li>GlobalJobListener 注册 JobExecutionLogger（自动写 sys_job_run_log）</li>
     *   <li>WaitForJobsToCompleteOnShutdown=true（优雅关闭，等待运行中 Job 完成）</li>
     *   <li>内存模式剔除继承到的 JDBC 专属 jobStore 属性，保留 RAM 与线程池属性</li>
     * </ul>
     *
     * <p>DataSource 由 QuartzAutoConfiguration.JdbcStoreTypeConfiguration 在
     * {@code spring.quartz.job-store-type=jdbc} 时自动注入；测试场景设
     * {@code spring.quartz.job-store-type=memory} 即走 RAMJobStore，不需要 DataSource。
     *
     * @param jobFactory  AutowiringSpringBeanJobFactory bean
     * @param jobListener JobExecutionLogger（全局 JobListener）
     * @param quartzProperties Spring Boot 绑定的 Quartz 配置
     * @return SchedulerFactoryBeanCustomizer
     */
    @Bean
    public SchedulerFactoryBeanCustomizer schedulerFactoryBeanCustomizer(
            AutowiringSpringBeanJobFactory jobFactory,
            JobExecutionLogger jobListener,
            QuartzProperties quartzProperties) {
        return bean -> {
            if (JobStoreType.MEMORY.equals(quartzProperties.getJobStoreType())) {
                bean.setQuartzProperties(memoryQuartzProperties(quartzProperties.getProperties()));
            }
            bean.setJobFactory(jobFactory);
            bean.setGlobalJobListeners(jobListener);
            bean.setWaitForJobsToCompleteOnShutdown(true);
        };
    }

    /**
     * 复制 Quartz 属性并移除 RAMJobStore 无法识别的 jobStore 配置。
     * 不修改 QuartzProperties 原始 Map，JDBC 模式继续由 Spring Boot 原样接管。
     */
    static Properties memoryQuartzProperties(Map<String, String> source) {
        Properties sanitized = new Properties();
        source.forEach((name, value) -> {
            if (name == null || value == null || value.isBlank()) {
                return;
            }
            if (name.startsWith(JOB_STORE_PREFIX)
                    && !MEMORY_JOB_STORE_PROPERTIES.contains(name)) {
                return;
            }
            sanitized.setProperty(name, value);
        });
        return sanitized;
    }
}
