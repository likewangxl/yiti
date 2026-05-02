package com.bank.branch.platform.performance.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PerfEngineProperties 配置绑定守护测试.
 *
 * <p>验证 V1.1 执行引擎配置类 {@code PerfEngineProperties} 能从 application 属性
 * {@code perf.engine.*} 正确绑定 4 个字段：
 * <ul>
 *     <li>{@code perf.engine.groovy-enabled} → groovyEnabled</li>
 *     <li>{@code perf.engine.sql-timeout-seconds} → sqlTimeoutSeconds</li>
 *     <li>{@code perf.engine.cascade-max-depth} → cascadeMaxDepth</li>
 *     <li>{@code perf.engine.import-batch-size} → importBatchSize</li>
 * </ul>
 *
 * <p>Red 阶段：PerfEngineProperties 类尚未创建，Spring 上下文启动或 @Autowired 注入失败。
 *
 * <p>Green 阶段：创建配置类 + {@code @Component} + {@code @ConfigurationProperties(prefix="perf.engine")} 后通过。
 */
@SpringBootTest(classes = {
    com.bank.branch.platform.performance.support.PerfTestApp.class,
    com.bank.branch.platform.performance.support.PerfTestConfig.class
})
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "perf.engine.groovy-enabled=false",
    "perf.engine.sql-timeout-seconds=45",
    "perf.engine.cascade-max-depth=7",
    "perf.engine.import-batch-size=1000",
    "spring.main.allow-bean-definition-overriding=true"
})
class PerfEnginePropertiesTest {

    @Autowired
    private PerfEngineProperties properties;

    /**
     * 4 个字段必须全部从 perf.engine.* 绑定生效.
     */
    @Test
    void bindsAllFieldsFromProperties() {
        assertThat(properties).isNotNull();
        assertThat(properties.isGroovyEnabled()).isFalse();
        assertThat(properties.getSqlTimeoutSeconds()).isEqualTo(45);
        assertThat(properties.getCascadeMaxDepth()).isEqualTo(7);
        assertThat(properties.getImportBatchSize()).isEqualTo(1000);
    }
}
