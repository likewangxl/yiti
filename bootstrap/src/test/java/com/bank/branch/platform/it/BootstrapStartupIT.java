package com.bank.branch.platform.it;

import com.bank.branch.platform.bridge.PerformanceMetricApiBridge;
import com.bank.branch.platform.it.config.TestMockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Option A 启动级集成测试（最小可信集）。
 *
 * <p>目的：在不接触业务功能的前提下，确认 9 模块（auth + governance + workflow + portal +
 * customer + bizapp + performance + report + bootstrap）的 Spring 装配链路完整：</p>
 * <ul>
 *   <li>ApplicationContext 启动无 BeanCreation/Conflict 异常</li>
 *   <li>各模块对外 *Api / *QueryApi 均可被 {@link ApplicationContext#getBean(Class)} 拿到</li>
 *   <li>portal MetricApi 真实由 bootstrap 桥接到 performance MetricApi（不是占位降级）</li>
 *   <li>RequestMappingHandlerMapping 注册的 /api/ 端点数 ≥ 141（最低底线）</li>
 * </ul>
 *
 * <p>测试 profile 用 H2 + Redis/Flowable mock，配置见 {@code application-test.yml} +
 * {@link TestMockConfig}。本类不验证 SQL/业务行为，只验证装配链路。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestMockConfig.class)
class BootstrapStartupIT {

    @Autowired
    private ApplicationContext context;

    @Autowired(required = false)
    private RequestMappingHandlerMapping handlerMapping;

    // ---------------------------------------------------------------------
    // case 1: ApplicationContext 启动 + bean 总数下限
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("启动级 IT - ApplicationContext 加载 + bean 数量 >= 200")
    void startup_applicationContextLoads() {
        assertThat(context).as("ApplicationContext 应注入非空").isNotNull();
        String[] beanNames = context.getBeanDefinitionNames();
        assertThat(beanNames.length)
                .as("Spring 容器至少应注册 200 个 bean（实际：%s）", beanNames.length)
                .isGreaterThanOrEqualTo(200);
    }

    // ---------------------------------------------------------------------
    // case 2: 各模块 *Api / *QueryApi 装配验证
    //
    // 每个模块挑代表性 Api 接口，循环调 context.getBean(class)，
    // 失败时收集所有失败项一起 fail，不在第一个失败处中断。
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("启动级 IT - 跨模块 *Api 全部装配为 Spring Bean")
    void startup_crossModule_apis_assembled() {
        List<Class<?>> apiClasses = List.of(
                // auth-permission-center（5 个）
                com.bank.branch.platform.auth.api.CurrentUserApi.class,
                com.bank.branch.platform.auth.api.OrgApi.class,
                com.bank.branch.platform.auth.api.BizScopeApi.class,
                com.bank.branch.platform.auth.api.ResourceApi.class,
                com.bank.branch.platform.auth.api.UserApi.class,
                // system-governance-center（5 个）
                com.bank.branch.platform.governance.api.DictApi.class,
                com.bank.branch.platform.governance.api.ConfigApi.class,
                com.bank.branch.platform.governance.api.CalendarApi.class,
                com.bank.branch.platform.governance.api.FileApi.class,
                com.bank.branch.platform.governance.api.AuditApi.class,
                // workflow-center（2 个）
                com.bank.branch.platform.workflow.api.WorkflowApi.class,
                com.bank.branch.platform.workflow.api.WorkflowQueryApi.class,
                // portal-content-center（5 个）
                com.bank.branch.platform.portal.api.ProductApi.class,
                com.bank.branch.platform.portal.api.PortalApi.class,
                com.bank.branch.platform.portal.api.DocumentApi.class,
                com.bank.branch.platform.portal.api.NavApi.class,
                com.bank.branch.platform.portal.api.AddressBookApi.class,
                // customer-marketing-center（5 个）
                com.bank.branch.platform.customer.api.CustomerQueryApi.class,
                com.bank.branch.platform.customer.api.TouchTaskQueryApi.class,
                com.bank.branch.platform.customer.api.TagApi.class,
                com.bank.branch.platform.customer.api.LeadApi.class,
                com.bank.branch.platform.customer.api.ClaimApi.class,
                // business-application-center（5 个）
                com.bank.branch.platform.bizapp.api.LoanApi.class,
                com.bank.branch.platform.bizapp.api.LoanQueryApi.class,
                com.bank.branch.platform.bizapp.api.SupportApi.class,
                com.bank.branch.platform.bizapp.api.SupportQueryApi.class,
                com.bank.branch.platform.bizapp.api.BizApplyQueryApi.class,
                // performance-engine-center（5 个）
                com.bank.branch.platform.performance.api.MetricApi.class,
                com.bank.branch.platform.performance.api.MetricQueryApi.class,
                com.bank.branch.platform.performance.api.KpiApi.class,
                com.bank.branch.platform.performance.api.TargetApi.class,
                com.bank.branch.platform.performance.api.AllocApi.class
                // report-analytics-center 不暴露 *Api，按设计跳过
        );

        List<String> failures = new ArrayList<>();
        for (Class<?> apiClass : apiClasses) {
            try {
                Object bean = context.getBean(apiClass);
                if (bean == null) {
                    failures.add(apiClass.getName() + " -> getBean 返回 null");
                }
            } catch (Exception ex) {
                failures.add(apiClass.getName() + " -> " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }

        assertThat(failures)
                .as("以下 *Api 接口未能成功装配（共 %s 项）：%n%s",
                        failures.size(), String.join("\n", failures))
                .isEmpty();
    }

    // ---------------------------------------------------------------------
    // case 3: PerformanceMetricApiBridge 实际注入到 portal MetricApi
    //
    // 防腐层抽象 portal.adapter.MetricApi 必须注入到 PerformanceMetricApiBridge，
    // 而非（可能存在的）占位降级实现。这一项是消除 V1 阶段
    // portal MetricAdapter 永远走降级返回空列表的关键守护。
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("启动级 IT - PerformanceMetricApiBridge 真实注入到 portal MetricApi")
    void startup_performanceMetricApiBridge_injected() {
        PerformanceMetricApiBridge bridge =
                context.getBean(PerformanceMetricApiBridge.class);
        assertThat(bridge)
                .as("bootstrap 应已注册 PerformanceMetricApiBridge bean")
                .isNotNull();

        com.bank.branch.platform.portal.adapter.MetricApi portalMetricApi =
                context.getBean(com.bank.branch.platform.portal.adapter.MetricApi.class);
        assertThat(portalMetricApi)
                .as("portal.adapter.MetricApi 应注入实际实现，而非降级 null")
                .isNotNull();

        // 受 AOP 切面（@AuditLog / @BizAuth）影响，bean 可能是 CGLIB / JDK 动态代理；
        // 用 AopUtils.getTargetClass() 拿真正的目标类，而非 $$SpringCGLIB$$0 等代理类。
        Class<?> targetClass = AopUtils.getTargetClass(portalMetricApi);
        assertThat(targetClass)
                .as("portal.adapter.MetricApi 真实目标类应为 PerformanceMetricApiBridge，"
                        + "实际类型：%s（代理类：%s）",
                        targetClass.getName(), portalMetricApi.getClass().getName())
                .isEqualTo(PerformanceMetricApiBridge.class);

        // 同时显式断言两个 bean 是同一个实例（getBean(impl) == getBean(interface)）
        assertThat(portalMetricApi)
                .as("portal.adapter.MetricApi 应该和 PerformanceMetricApiBridge bean 是同一实例")
                .isSameAs(bridge);
    }

    // ---------------------------------------------------------------------
    // case 4: RequestMappingHandlerMapping 注册的 /api/ 端点数 >= 141
    //
    // 期望底线：perf 35 + report 25 + portal 24 + customer 36 + bizapp 21 = 141。
    // 实际包含 auth + governance + workflow 等更多端点，应轻松超过此底线。
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("启动级 IT - REST /api/ 端点注册数 >= 141")
    void startup_restEndpoints_registered_at_least_141() {
        assertThat(handlerMapping)
                .as("RequestMappingHandlerMapping 必须存在")
                .isNotNull();

        Set<String> apiPaths = collectApiPaths(handlerMapping);
        if (apiPaths.size() < 141) {
            // 失败时打印所有路径辅助诊断
            apiPaths.stream().sorted().forEach(p -> System.err.println("[endpoint] " + p));
        }
        assertThat(apiPaths.size())
                .as("RequestMappingHandlerMapping 应注册至少 141 个 /api/ 端点（实际 %s）",
                        apiPaths.size())
                .isGreaterThanOrEqualTo(141);
    }

    // ---------------------------------------------------------------------
    // 辅助：收集所有以 /api/ 开头的端点 url pattern（与 method 解耦，纯路径去重）。
    //
    // Spring 6 默认走 PathPatternsRequestCondition；旧应用可能走 PatternsRequestCondition。
    // 双兼容：先尝试 PathPatternsCondition.getPatternValues()，
    // 没有再退到 PatternsCondition.getPatterns()。
    // ---------------------------------------------------------------------
    private static Set<String> collectApiPaths(RequestMappingHandlerMapping mapping) {
        Set<String> paths = new LinkedHashSet<>();
        Map<RequestMappingInfo, ?> handlerMethods = mapping.getHandlerMethods();
        for (RequestMappingInfo info : handlerMethods.keySet()) {
            Set<String> patterns = extractPatterns(info);
            for (String pattern : patterns) {
                if (pattern != null && pattern.startsWith("/api/")) {
                    paths.add(pattern);
                }
            }
        }
        return paths;
    }

    /**
     * 双兼容提取 RequestMappingInfo 的所有 url pattern。
     */
    private static Set<String> extractPatterns(RequestMappingInfo info) {
        // Spring 6 PathPatternsCondition（默认）
        if (info.getPathPatternsCondition() != null) {
            return info.getPathPatternsCondition().getPatternValues();
        }
        // 旧 PatternsCondition 兜底
        if (info.getPatternsCondition() != null) {
            return info.getPatternsCondition().getPatterns();
        }
        return Set.of();
    }
}
