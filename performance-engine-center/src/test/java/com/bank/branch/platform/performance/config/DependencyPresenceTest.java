package com.bank.branch.platform.performance.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * V1.1 依赖落位守护测试.
 *
 * <p>验证 V1.1 规划新增的两项核心依赖（Groovy 与 EasyExcel）是否在类路径上可见：
 * <ul>
 *     <li>{@code groovy.lang.GroovyShell} —— V1.1 指标公式执行引擎，用于解释型公式求值</li>
 *     <li>{@code com.alibaba.excel.EasyExcel} —— V1.1 数据导入/导出入口</li>
 * </ul>
 *
 * <p>Red 阶段：pom.xml 尚未声明 groovy 与 easyexcel 依赖，Class.forName 应抛 ClassNotFoundException。
 *
 * <p>Green 阶段：pom.xml 追加 org.apache.groovy:groovy:4.0.21 与 com.alibaba:easyexcel:3.3.4 后通过。
 */
class DependencyPresenceTest {

    /**
     * Groovy 4.0.21 的核心入口类 {@link groovy.lang.GroovyShell} 必须可加载.
     */
    @Test
    void groovyShell_available() {
        assertThatCode(() -> {
            Class<?> clazz = Class.forName("groovy.lang.GroovyShell");
            assertThat(clazz).isNotNull();
        }).doesNotThrowAnyException();
    }

    /**
     * EasyExcel 3.3.4 的核心入口类 {@link com.alibaba.excel.EasyExcel} 必须可加载.
     */
    @Test
    void easyExcel_available() {
        assertThatCode(() -> {
            Class<?> clazz = Class.forName("com.alibaba.excel.EasyExcel");
            assertThat(clazz).isNotNull();
        }).doesNotThrowAnyException();
    }
}
