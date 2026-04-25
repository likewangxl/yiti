package com.bank.branch.platform.governance.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.spi.OperableTrigger;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AutowiringSpringBeanJobFactory 单元测试
 *
 * <p>验证：
 * <ol>
 *   <li>createJobInstance 正常场景：实例化 Job 后调用 autowireBean 完成 @Autowired 字段注入</li>
 *   <li>createJobInstance 异常场景：未 setApplicationContext 时抛 IllegalStateException</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class AutowiringSpringBeanJobFactoryTest {

    @Mock private ApplicationContext applicationContext;
    @Mock private AutowireCapableBeanFactory autowireFactory;
    @Mock private TriggerFiredBundle bundle;
    @Mock private Scheduler scheduler;
    @Mock private JobDetail jobDetail;
    @Mock private OperableTrigger trigger;

    @Test
    void createJobInstance_invokesAutowireBean() throws Exception {
        // 注意：必须先 stub applicationContext.getAutowireCapableBeanFactory()，
        // 因为 setApplicationContext 内部会立刻调用它解析 beanFactory（eager 解析）。
        when(applicationContext.getAutowireCapableBeanFactory()).thenReturn(autowireFactory);

        AutowiringSpringBeanJobFactory factory = new AutowiringSpringBeanJobFactory();
        factory.setApplicationContext(applicationContext);

        // 准备一个普通的 Job class
        when(bundle.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getJobClass()).thenReturn((Class) TestJob.class);

        // SpringBeanJobFactory#createJobInstance 在 isEligibleForPropertyPopulation 为 true 时
        // 会读取 JobDetail.jobDataMap 与 Trigger.jobDataMap 来填充 Job 属性，
        // 这里提供空 JobDataMap 让其顺利走完该路径。
        when(jobDetail.getJobDataMap()).thenReturn(new JobDataMap());
        when(bundle.getTrigger()).thenReturn(trigger);
        when(trigger.getJobDataMap()).thenReturn(new JobDataMap());

        Object job = factory.createJobInstance(bundle);

        assertThat(job).isInstanceOf(TestJob.class);
        verify(autowireFactory).autowireBean(job);
    }

    @Test
    void createJobInstance_withoutApplicationContext_throwsIllegalStateException() {
        AutowiringSpringBeanJobFactory factory = new AutowiringSpringBeanJobFactory();
        // 不调 setApplicationContext —— beanFactory 保持 null

        // 注：此处不 stub bundle.getJobDetail()/jobDetail.getJobClass()，
        // 因为实现会先做 beanFactory == null 检查并抛异常，永不会用到 bundle 内容。
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> factory.createJobInstance(bundle))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ApplicationContext");
    }

    public static class TestJob implements org.quartz.Job {
        @Override public void execute(org.quartz.JobExecutionContext context) {}
    }
}
