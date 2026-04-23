package com.bank.branch.platform.performance.support;

import org.flowable.spring.boot.EndpointAutoConfiguration;
import org.flowable.spring.boot.ProcessEngineAutoConfiguration;
import org.flowable.spring.boot.ProcessEngineServicesAutoConfiguration;
import org.flowable.spring.boot.RestApiAutoConfiguration;
import org.flowable.spring.boot.actuate.info.FlowableInfoAutoConfiguration;
import org.flowable.spring.boot.app.AppEngineAutoConfiguration;
import org.flowable.spring.boot.app.AppEngineServicesAutoConfiguration;
import org.flowable.spring.boot.cmmn.CmmnEngineAutoConfiguration;
import org.flowable.spring.boot.cmmn.CmmnEngineServicesAutoConfiguration;
import org.flowable.spring.boot.dmn.DmnEngineAutoConfiguration;
import org.flowable.spring.boot.dmn.DmnEngineServicesAutoConfiguration;
import org.flowable.spring.boot.eventregistry.EventRegistryAutoConfiguration;
import org.flowable.spring.boot.eventregistry.EventRegistryServicesAutoConfiguration;
import org.flowable.spring.boot.idm.IdmEngineAutoConfiguration;
import org.flowable.spring.boot.idm.IdmEngineServicesAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 测试专用 Spring Boot 启动类.
 * <p>仅扫描 performance 子包, 避免加载其他模块无关的 Bean.
 *
 * <p>V1.2 Phase Q0.1 起 performance-engine-center 引入 workflow-center 传递依赖，
 * 会将 flowable-spring-boot-autoconfigure 带入测试 classpath. performance 自身测试
 * 不使用 Flowable 真实 ProcessEngine（ACT_* 表未初始化），故统一排除所有 Flowable
 * AutoConfig；需要 ProcessEngine 真实实例的场景请在 bootstrap 级集成测试中验证.
 */
@SpringBootApplication(
    scanBasePackages = "com.bank.branch.platform.performance",
    exclude = {
        ProcessEngineAutoConfiguration.class,
        ProcessEngineServicesAutoConfiguration.class,
        AppEngineAutoConfiguration.class,
        AppEngineServicesAutoConfiguration.class,
        IdmEngineAutoConfiguration.class,
        IdmEngineServicesAutoConfiguration.class,
        EventRegistryAutoConfiguration.class,
        EventRegistryServicesAutoConfiguration.class,
        DmnEngineAutoConfiguration.class,
        DmnEngineServicesAutoConfiguration.class,
        CmmnEngineAutoConfiguration.class,
        CmmnEngineServicesAutoConfiguration.class,
        EndpointAutoConfiguration.class,
        RestApiAutoConfiguration.class,
        FlowableInfoAutoConfiguration.class
    }
)
public class PerfTestApp {
    public static void main(String[] args) {
        SpringApplication.run(PerfTestApp.class, args);
    }
}
