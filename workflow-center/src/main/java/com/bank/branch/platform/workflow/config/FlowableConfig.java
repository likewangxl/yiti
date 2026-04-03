package com.bank.branch.platform.workflow.config;

import org.springframework.context.annotation.Configuration;

/**
 * Flowable 引擎配置
 * - history-level 通过 application.yml 配置为 audit
 * - IDM 引擎通过 application.yml 禁用
 * - Listener 注册：TaskAssignmentListener 和 ProcessCompletedListener 作为 @Component
 *   由 Spring 管理，在 BPMN 流程定义 XML 中通过 delegateExpression 引用
 *
 * application.yml 配置项（由 bootstrap 模块管理）：
 *   flowable:
 *     history-level: audit
 *     idm:
 *       enabled: false
 *     database-schema-update: true
 */
@Configuration
public class FlowableConfig {
    // V1: Flowable 引擎配置通过 Spring Boot auto-configuration + application.yml 管理
    // Listener 作为 @Component Bean，在 BPMN XML 中通过 ${taskAssignmentListener} 引用
    // 后续版本可在此类中自定义 ProcessEngineConfiguration
}
