package com.bank.branch.platform.workflow.support;

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
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * workflow-center Mapper IT 专用启动配置。
 *
 * <p><b>scanBasePackages 指向 support 包</b>（仅含测试辅助类），因此<b>不</b>扫描
 * controller / service / listener —— 这些 Bean 不创建，就无需 Flowable ProcessEngine、
 * 也无需 mock 跨模块 API。</p>
 *
 * <p>用完整 {@code @EnableAutoConfiguration}（经 {@code @SpringBootApplication}）可靠装配
 * 数据源 + MyBatis-Plus SqlSessionFactory；{@code @MapperScan} 显式扫描 workflow 的 Mapper
 * 接口包（而非本类所在的 support 包）。排除全部 Flowable AutoConfig，避免 ProcessEngine 初始化。</p>
 *
 * <p>数据源走 application-test.yml（本地 yiti 库），WF_FLOW_* 等表均已存在。
 * Mapper XML 由 mapper-locations（classpath*:mapper/**&#47;*Mapper.xml）加载。</p>
 */
@SpringBootApplication(
        scanBasePackages = "com.bank.branch.platform.workflow.support",
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
@MapperScan("com.bank.branch.platform.workflow.mapper")
public class WfTestApp {
}
