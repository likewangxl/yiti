package com.bank.branch.platform.config;

import com.bank.branch.platform.governance.storage.ObsStorageClient;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.service.EvalImportCompensation;
import com.bank.branch.platform.soap.config.SidecarProbe;
import com.bank.branch.platform.soap.config.SidecarRegistrationChecker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * screen-scope-e2e profile 与 bootstrap 实际依赖组件之间的运行时隔离契约。
 *
 * <p>bootstrap 以 Maven 外部模块依赖运行；本测试故意不直接编译上游源码，而是在 bootstrap 的
 * 测试类路径中装配解析到的依赖类。这样可防止当前源码已加隔离开关、但本地仓库 SNAPSHOT 仍为旧类时
 * 让真实 {@code spring-boot:run} 绕过开关。</p>
 *
 * <p>测试只创建内存 ApplicationContext：边车与 Mapper 都是无操作替身，既不启动应用、也不连接
 * 数据库或网络。OBS 仅反射其隔离闸门，绝不构造 {@code ObsStorageClient}。</p>
 */
class ScreenScopeE2eRuntimeIsolationTest {

    private static final String PROFILE_RESOURCE = "application-screen-scope-e2e.yml";

    @Test
    void profilePropertiesAreFalseInSpringEnvironmentAndActualDependenciesHonorThem() {
        List<String> sidecarRequests = new ArrayList<>();
        AtomicBoolean sidecarCheckerPresent = new AtomicBoolean();
        AtomicBoolean compensationPresent = new AtomicBoolean();
        AtomicBoolean sidecarPropertyDisabled = new AtomicBoolean();
        AtomicBoolean compensationPropertyDisabled = new AtomicBoolean();
        AtomicBoolean obsPropertyDisabled = new AtomicBoolean();

        SidecarProbe noOpProbe = path -> {
            sidecarRequests.add(path);
            return "0";
        };

        new ApplicationContextRunner()
                .withInitializer(context -> addScreenScopeProfile(context.getEnvironment().getPropertySources()))
                .withBean(SidecarProbe.class, () -> noOpProbe)
                .withBean(EvalAssignBatchMapper.class, ScreenScopeE2eRuntimeIsolationTest::noOpMapper)
                .withUserConfiguration(IsolationComponents.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    sidecarPropertyDisabled.set(Boolean.FALSE.equals(context.getEnvironment().getProperty(
                            "platform.sidecar.registration.enabled", Boolean.class)));
                    compensationPropertyDisabled.set(Boolean.FALSE.equals(context.getEnvironment().getProperty(
                            "perf.eval.import.compensation.enabled", Boolean.class)));
                    obsPropertyDisabled.set(Boolean.FALSE.equals(context.getEnvironment().getProperty(
                            "obs.enabled", Boolean.class)));
                    sidecarCheckerPresent.set(context.containsBean("sidecarRegistrationChecker"));
                    compensationPresent.set(context.containsBean("evalImportCompensation"));
                });

        assertSoftly(softly -> {
            softly.assertThat(sidecarPropertyDisabled.get())
                    .as("screen-scope-e2e 中 sidecar 注册开关必须在 Spring Environment 中为 false")
                    .isTrue();
            softly.assertThat(compensationPropertyDisabled.get())
                    .as("screen-scope-e2e 中导入补偿开关必须在 Spring Environment 中为 false")
                    .isTrue();
            softly.assertThat(obsPropertyDisabled.get())
                    .as("screen-scope-e2e 中 OBS 开关必须在 Spring Environment 中为 false")
                    .isTrue();
            softly.assertThat(sidecarCheckerPresent.get())
                    .as("关闭 sidecar 注册时，实际 bootstrap 依赖不得装配注册器")
                    .isFalse();
            softly.assertThat(compensationPresent.get())
                    .as("关闭导入补偿时，实际 bootstrap 依赖不得装配补偿器")
                    .isFalse();
            softly.assertThat(sidecarRequests)
                    .as("关闭 sidecar 注册时，上下文销毁也不得调用 /down")
                    .isEmpty();
            softly.assertThat(obsClientHasDisabledGate())
                    .as("实际 bootstrap 依赖的 OBS 客户端必须识别 obs.enabled=false")
                    .isTrue();
        });
    }

    private static void addScreenScopeProfile(MutablePropertySources propertySources) {
        Resource resource = new ClassPathResource(PROFILE_RESOURCE);
        try {
            List<PropertySource<?>> loaded = new YamlPropertySourceLoader().load(PROFILE_RESOURCE, resource);
            assertThat(loaded).hasSize(1);
            propertySources.addFirst(loaded.get(0));
        } catch (IOException e) {
            throw new IllegalStateException("无法加载 screen-scope-e2e profile", e);
        }
    }

    private static EvalAssignBatchMapper noOpMapper() {
        return (EvalAssignBatchMapper) Proxy.newProxyInstance(
                EvalAssignBatchMapper.class.getClassLoader(),
                new Class<?>[]{EvalAssignBatchMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "screen-scope-no-op-eval-assign-batch-mapper";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    throw new AssertionError("隔离装配测试不得调用 Mapper: " + method.getName());
                });
    }

    private static boolean obsClientHasDisabledGate() {
        try {
            Field enabled = ObsStorageClient.class.getDeclaredField("enabled");
            Value value = enabled.getAnnotation(Value.class);
            Method requireClient = ObsStorageClient.class.getDeclaredMethod("requireClient", String.class);
            return enabled.getType() == boolean.class
                    && value != null
                    && "${obs.enabled:true}".equals(value.value())
                    && requireClient.getReturnType().getName().equals("com.obs.services.ObsClient");
        } catch (NoSuchFieldException | NoSuchMethodException e) {
            return false;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackageClasses = {SidecarRegistrationChecker.class, EvalImportCompensation.class},
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {SidecarRegistrationChecker.class, EvalImportCompensation.class})
    )
    static class IsolationComponents {
    }
}
