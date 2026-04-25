package com.bank.branch.platform.governance.config;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * 支持 @Autowired 的 Quartz JobFactory.
 *
 * <p>Quartz 通过反射 newInstance() 创建 Job 对象，不经过 Spring，导致 @Autowired 字段无法注入。
 * 本类继承 Spring 自带 SpringBeanJobFactory（仅注入 JobDataMap 字段），扩展为：
 * 反射创建 Job 后调用 applicationContext.getAutowireCapableBeanFactory().autowireBean(job)
 * 完成 Spring Bean 字段注入。
 *
 * <p>用法：在 QuartzConfig 中将本类注册为 SchedulerFactoryBean.setJobFactory()。
 */
public class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory
        implements ApplicationContextAware {

    private transient AutowireCapableBeanFactory beanFactory;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        this.beanFactory = context.getAutowireCapableBeanFactory();
    }

    @Override
    protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
        if (beanFactory == null) {
            throw new IllegalStateException(
                "ApplicationContext not set; AutowiringSpringBeanJobFactory cannot autowire job");
        }
        Object job = super.createJobInstance(bundle);
        beanFactory.autowireBean(job);
        return job;
    }
}
