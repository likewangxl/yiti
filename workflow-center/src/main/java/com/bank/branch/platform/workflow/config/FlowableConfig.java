package com.bank.branch.platform.workflow.config;

import org.springframework.context.annotation.Configuration;

/**
 * Flowable 引擎配置
 *
 * 本配置类**不再**手动创建 ProcessEngineConfiguration 和 ProcessEngine Bean。
 *
 * 原因：
 * 原始版本通过 `new SpringProcessEngineConfiguration()` 创建裸配置，绕过了
 * `flowable-spring-boot-starter` 自动配置的 `configureEngine()` 方法。
 * 自动配置会从 application.yml 读取 `flowable.database-schema-update: true`
 * 并正确设置 `databaseSchemaUpdate` 属性，触发 Flowable 表自动创建。
 *
 * 如果手动创建配置而不调用 `configureEngine()`，`databaseSchemaUpdate` 保持
 * 默认值 (FALSE)，导致 Flowable 走 `schemaCheckVersion()` 分支而不是 `schemaUpdate()`
 * 分支，在查询 `ACT_GE_PROPERTY` 表时因表不存在而抛出 NPE。
 *
 * 当前方案：
 * - 保留本类作为占位符，用于未来可能的自定义 ProcessEngineConfigurator
 * - Flowable ProcessEngine 完全由 `flowable-spring-boot-starter` 自动配置创建
 * - 所有配置通过 bootstrap/src/main/resources/application.yml 管理
 *
 * application.yml 中的 Flowable 配置：
 *   flowable:
 *     history-level: audit
 *     idm.enabled: false
 *     database-schema-update: true
 *     app.enabled: false
 *     eventregistry.enabled: false
 *     dmn.enabled: false
 *     cmmn.enabled: false
 *     form.enabled: false
 *
 * Flowable 监听器 (TaskAssignmentListener 和 ProcessCompletedListener) 已通过
 * @Component 注解注册，无需在本配置类中注册。
 *
 * @see <a href="https://flowable.com/open-source/docs/bpmn/ch05-Configuration/">Flowable Configuration</a>
 */
@Configuration
public class FlowableConfig {

    // ProcessEngineConfiguration 和 ProcessEngine Bean 已由
    // flowable-spring-boot-starter 的 ProcessEngineAutoConfiguration
    // 自动创建 (读取 application.yml 中的 flowable.* 配置)
    //
    // 不需要手动创建 Bean，否则会绕过自动配置的 configureEngine() 方法，
    // 导致 databaseSchemaUpdate 属性未正确设置，Flowable 表无法自动创建。
}
