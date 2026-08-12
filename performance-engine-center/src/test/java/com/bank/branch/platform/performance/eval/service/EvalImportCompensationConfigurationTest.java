package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.boot.ApplicationRunner;

import java.lang.reflect.Proxy;

/**
 * {@link EvalImportCompensation} 的条件装配测试：隔离启动时不得注册任何补偿触发入口。
 */
class EvalImportCompensationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EvalImportCompensationOnlyConfiguration.class)
            .withBean(EvalAssignBatchMapper.class, EvalImportCompensationConfigurationTest::noOpMapper);

    @Test
    @DisplayName("未配置开关时保持既有行为：补偿器作为 ApplicationRunner 装配")
    void defaultConfiguration_assemblesCompensationRunner() {
        contextRunner.run(context -> {
            org.assertj.core.api.Assertions.assertThat(context)
                    .hasSingleBean(EvalImportCompensation.class);
            org.assertj.core.api.Assertions.assertThat(context)
                    .hasSingleBean(ApplicationRunner.class);
        });
    }

    @Test
    @DisplayName("关闭补偿开关时不装配 ApplicationRunner，也不存在承载 Scheduled 方法的组件")
    void compensationDisabled_doesNotAssembleRunnerOrScheduledComponent() {
        contextRunner.withPropertyValues("perf.eval.import.compensation.enabled=false")
                .run(context -> {
                    org.assertj.core.api.Assertions.assertThat(context)
                            .doesNotHaveBean(EvalImportCompensation.class);
                    org.assertj.core.api.Assertions.assertThat(context)
                            .doesNotHaveBean(ApplicationRunner.class);
                });
    }

    private static EvalAssignBatchMapper noOpMapper() {
        return (EvalAssignBatchMapper) Proxy.newProxyInstance(
                EvalAssignBatchMapper.class.getClassLoader(),
                new Class<?>[]{EvalAssignBatchMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "no-op-eval-assign-batch-mapper";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    throw new UnsupportedOperationException("本装配测试不允许访问数据库");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = EvalImportCompensation.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = EvalImportCompensation.class))
    static class EvalImportCompensationOnlyConfiguration {
    }
}
