package com.bank.branch.platform.performance.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * V1.2 Phase Q0.1 依赖落位守护测试.
 *
 * <p>验证 V1.2 规划新增的两项核心模块依赖（workflow-center + customer-marketing-center）是否在类路径上可见：
 * <ul>
 *     <li>{@code com.bank.branch.platform.workflow.api.WorkflowApi} —— V1.2 启动 BPMN 流程、查询 businessKey</li>
 *     <li>{@code com.bank.branch.platform.customer.api.CustomerQueryApi} —— V1.2 分配调整审批时校验客户存在</li>
 *     <li>{@code com.bank.branch.platform.customer.api.ClaimApi} —— V1.2 分配调整审批时校验认领关系</li>
 * </ul>
 *
 * <p>Red 阶段：performance-engine-center/pom.xml 尚未声明 workflow-center 与 customer-marketing-center 依赖，
 * Class.forName 应抛 ClassNotFoundException。
 *
 * <p>Green 阶段：pom.xml 追加两项依赖后通过。
 *
 * <p>说明：本测试仅验证依赖类在编译期可见。Spring Bean 的实际装配由各模块自身的自动配置负责，
 * 不在本测试范围（performance 的 PerfTestApp 只扫描 performance 子包）。
 */
class V12DependencyPresenceTest {

    /**
     * workflow-center 的 {@code WorkflowApi} 必须可加载.
     */
    @Test
    void workflowApi_available() {
        assertThatCode(() -> {
            Class<?> clazz = Class.forName("com.bank.branch.platform.workflow.api.WorkflowApi");
            assertThat(clazz).isNotNull();
            assertThat(clazz.isInterface()).isTrue();
        }).doesNotThrowAnyException();
    }

    /**
     * workflow-center 的 {@code StartProcessCmd} DTO 必须可加载.
     *
     * <p>V1.2 将通过 WorkflowApi.startProcess(StartProcessCmd) 启动调整审批流程.
     */
    @Test
    void startProcessCmd_available() {
        assertThatCode(() -> {
            Class<?> clazz = Class.forName("com.bank.branch.platform.workflow.api.dto.StartProcessCmd");
            assertThat(clazz).isNotNull();
        }).doesNotThrowAnyException();
    }

    /**
     * customer-marketing-center 的 {@code CustomerQueryApi} 必须可加载.
     *
     * <p>V1.2 分配调整提交时调用 {@code CustomerQueryApi.getCustomer} 校验客户存在与归属.
     */
    @Test
    void customerQueryApi_available() {
        assertThatCode(() -> {
            Class<?> clazz = Class.forName("com.bank.branch.platform.customer.api.CustomerQueryApi");
            assertThat(clazz).isNotNull();
            assertThat(clazz.isInterface()).isTrue();
        }).doesNotThrowAnyException();
    }

    /**
     * customer-marketing-center 的 {@code ClaimApi} 必须可加载.
     *
     * <p>V1.2 分配调整时可能需要校验当前认领关系，用于权限与一致性判断.
     */
    @Test
    void claimApi_available() {
        assertThatCode(() -> {
            Class<?> clazz = Class.forName("com.bank.branch.platform.customer.api.ClaimApi");
            assertThat(clazz).isNotNull();
            assertThat(clazz.isInterface()).isTrue();
        }).doesNotThrowAnyException();
    }
}
