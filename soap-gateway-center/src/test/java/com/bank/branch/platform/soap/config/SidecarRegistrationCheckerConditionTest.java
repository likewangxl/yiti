package com.bank.branch.platform.soap.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Sidecar 注册器的条件装配测试。
 *
 * <p>测试使用内存中的 {@link SidecarProbe}，不会连接真实边车；关闭上下文时记录的请求可覆盖
 * {@code @PreDestroy} 路径。</p>
 */
class SidecarRegistrationCheckerConditionTest {

    @Test
    void registrationDisabled_doesNotCreateCheckerOrCallSidecarOnContextClose() {
        List<String> requestedPaths = new ArrayList<>();
        AtomicBoolean checkerPresent = new AtomicBoolean();
        SidecarProbe recordingProbe = path -> {
            requestedPaths.add(path);
            return "0";
        };

        new ApplicationContextRunner()
                .withPropertyValues("platform.sidecar.registration.enabled=false")
                .withBean(SidecarProbe.class, () -> recordingProbe)
                .withUserConfiguration(SidecarRegistrationChecker.class)
                .run(context -> checkerPresent.set(context.containsBean("sidecarRegistrationChecker")));

        assertSoftly(softly -> {
            softly.assertThat(checkerPresent.get())
                    .as("关闭注册时不得创建 SidecarRegistrationChecker")
                    .isFalse();
            softly.assertThat(requestedPaths)
                    .as("关闭注册时上下文销毁不得调用边车")
                    .isEmpty();
        });
    }

    @Test
    void registrationEnabledByDefault_createsChecker() {
        new ApplicationContextRunner()
                .withBean(SidecarProbe.class, () -> path -> "0")
                .withUserConfiguration(SidecarRegistrationChecker.class)
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(SidecarRegistrationChecker.class));
    }
}
