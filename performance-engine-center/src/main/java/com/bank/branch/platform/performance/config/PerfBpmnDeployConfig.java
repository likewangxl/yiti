package com.bank.branch.platform.performance.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * V1.2 Phase Q0.3 BPMN 部署占位配置.
 *
 * <p>本配置类是 performance-engine-center V1.2 的 BPMN 显式部署入口占位；当前版本不主动
 * 调用 Flowable {@code RepositoryService.createDeployment()}，原因如下：
 *
 * <ul>
 *   <li>bootstrap 的 {@code application.yml} 已配置
 *       {@code flowable.process-definition-location-prefix=classpath*:/bpmn/}
 *       与 {@code flowable.process-definition-location-suffixes=**.bpmn20.xml}，
 *       Flowable starter 启动时会自动扫描所有模块 classpath 下的 {@code bpmn/} 目录
 *       并部署 BPMN 文件（与 workflow-center 的 {@code loan_approve_v1.bpmn20.xml}
 *       采用同一机制），无需 performance 模块手动部署；</li>
 *   <li>performance 自身测试环境不启动 Flowable ProcessEngine（ACT_* 表未建），
 *       若在此注入 {@code RepositoryService} 会导致 performance 测试 context 加载失败；</li>
 *   <li>V1.2 Q2/Q3 正式实现分配调整 / 目标修正审批时，若需条件化部署（如按 profile 禁用、
 *       按版本号分环境）可在此配置类增补 {@code ApplicationRunner} 或 {@code @PostConstruct}
 *       逻辑，通过 {@code @ConditionalOnBean(RepositoryService.class)} 守护.</li>
 * </ul>
 *
 * <p>本 Phase（Q0.3）的交付目标：
 * <ol>
 *   <li>3 份 BPMN XML 占位已在 {@code src/main/resources/bpmn/} 就位，bootstrap 启动时
 *       由 Flowable starter 自动部署；</li>
 *   <li>本配置类作为 V1.2 正式实现时的显式部署入口标识；</li>
 *   <li>{@link PerfBpmnDeployConfigIT} 守护 3 份 BPMN 资源存在且 processDefinitionKey 正确.</li>
 * </ol>
 *
 * @see PerfBpmnDeployConfigIT
 */
@Slf4j
@Configuration
public class PerfBpmnDeployConfig {

    // V1.2 Phase Q0.3：仅作显式占位；bootstrap 级 Flowable starter 自动部署 classpath:bpmn/ 下全部 BPMN 文件.
    // V1.2 Q2/Q3 正式实现时可在此注入 RepositoryService 做条件化显式部署.
}
