package com.bank.branch.platform.report;

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
import org.springframework.context.annotation.Import;

/**
 * report-analytics-center 测试专用 Spring Boot 启动类（M0.2.1）.
 *
 * <p>仅扫描 report 子包，避免加载 perf / customer / workflow / governance 等其他业务模块
 * 的真实 Bean。后续 M1+ 跨模块查询会通过 {@code @MockBean} 替换上游 Api。
 *
 * <p>排除 Flowable 全部 AutoConfiguration（performance-engine-center 引入 workflow-center
 * 传递依赖会把 flowable-spring-boot-autoconfigure 带进 classpath，但 report 不需要）。
 *
 * <p>对齐 perf 模块 {@code PerfTestApp} 的同款排除策略，保持跨模块测试启动一致性。
 */
@SpringBootApplication(
    scanBasePackages = "com.bank.branch.platform.report",
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
@Import(TestUpstreamApiMockConfig.class)
public class ReportTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReportTestApplication.class, args);
    }
}
