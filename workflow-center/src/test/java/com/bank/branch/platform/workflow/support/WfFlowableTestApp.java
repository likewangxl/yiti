package com.bank.branch.platform.workflow.support;

import org.flowable.spring.boot.app.AppEngineAutoConfiguration;
import org.flowable.spring.boot.app.AppEngineServicesAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * workflow-center「全量 Flowable」IT 专用启动配置。
 *
 * <p>与 {@link WfTestApp}（Mapper 切片版，<b>排除</b> Flowable，仅装配数据源 + Mapper）不同，
 * 本类<b>不</b>排除任何 Flowable AutoConfiguration —— 需要真实的 {@code ProcessEngine}、
 * {@code RepositoryService}、{@code RuntimeService}、{@code TaskService}，以便
 * {@link com.bank.branch.platform.workflow.service.flow.FlowPublishService} 真部署生成的 BPMN，
 * 并跑通流程流转。</p>
 *
 * <p>组件扫描覆盖整个 workflow 包，因此 service / listener / config 等 Bean 全部创建。
 * 其中跨模块协作者（{@code UserApi} / {@code OrgApi} / {@code CurrentUserApi} /
 * {@code NotifyApi} / {@code CalendarApi}）来自 auth / governance，本包扫描不到真实现，
 * 须由 IT 用 {@code @MockBean} 提供。</p>
 *
 * <p><b>为什么不用 {@code @SpringBootApplication}：</b>{@code scanBasePackages} 必须覆盖到
 * {@code support} 包（含 service 的兄弟包），而本包内还有另一个 {@code @SpringBootApplication}
 * —— {@link WfTestApp}（Mapper 切片版，{@code exclude} 了 {@code ProcessEngine*AutoConfiguration}）。
 * 组件扫描会把 {@link WfTestApp} 当成嵌套 {@code @Configuration} 扫进来，其
 * {@code @EnableAutoConfiguration(exclude=...)} 会被合并生效，把 Flowable 自动配置整体排除，
 * 导致 {@code RepositoryService} 等 Bean 不创建（表现为 conditions report 全 None /
 * "No qualifying bean of type RepositoryService"）。{@code @SpringBootApplication} 不暴露
 * {@code excludeFilters}，故拆成 {@code @SpringBootConfiguration + @EnableAutoConfiguration +
 * @ComponentScan}，用 {@code excludeFilters} 把 {@link WfTestApp} 从扫描中剔除。</p>
 *
 * <p>排除 AppEngine 自动配置 → 触发 ProcessEngineServicesAutoConfiguration 的 standalone
 * 路径建出 ProcessEngine + RepositoryService/RuntimeService/TaskService/HistoryService。</p>
 *
 * <p>数据源 + Flowable 配置走 application-test.yml（本地 yiti 库），
 * ACT_* 与 WF_FLOW_* 等表均已存在。</p>
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        AppEngineAutoConfiguration.class,
        AppEngineServicesAutoConfiguration.class
})
@ComponentScan(
        basePackages = "com.bank.branch.platform.workflow",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = WfTestApp.class))
@MapperScan("com.bank.branch.platform.workflow.mapper")
public class WfFlowableTestApp {
}
