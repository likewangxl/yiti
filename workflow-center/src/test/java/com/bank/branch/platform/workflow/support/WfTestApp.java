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
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * workflow-center Mapper IT 专用启动配置（纯 MyBatis 切片）。
 *
 * <p><b>故意不使用 {@code @ComponentScan}</b>（即不用 {@code @SpringBootApplication}）：
 * 一旦组件扫描 support 包，会把同包的 {@code WfFlowableTestApp}（它 {@code @ComponentScan}
 * 整个 workflow 包）当嵌套配置导入，从而拉起 controller/service 并要求 Flowable
 * RepositoryService，导致 Mapper IT 上下文加载失败。改用
 * {@code @SpringBootConfiguration + @EnableAutoConfiguration + @MapperScan} 组合：
 * 完整 auto-config 可靠装配数据源 + MyBatis-Plus SqlSessionFactory，
 * {@code @MapperScan} 显式只扫 mapper 接口包，全程不做组件扫描。</p>
 *
 * <p>排除全部 Flowable AutoConfig，避免 ProcessEngine 初始化。
 * 数据源走 application-test.yml（本地 yiti 库），WF_FLOW_* 等表均已存在。
 * Mapper XML 由 mapper-locations（classpath*:mapper/**&#47;*Mapper.xml）加载。</p>
 */
@SpringBootConfiguration
@EnableAutoConfiguration(
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
