package com.bank.branch.platform.performance.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Level1/2/3 指标批量计算定时任务实例化守护测试.
 *
 * <p>Quartz 通过 AutowiringSpringBeanJobFactory.createJobInstance → SpringBeanJobFactory
 * 的反射 {@code newInstance()} 创建 Job，**必须有无参构造**，之后再 autowireBean 注入字段。
 * 历史上这三个 Job 误用构造器注入（无无参构造），导致每次触发抛
 * {@code NoSuchMethodException: <init>()}、触发器进 ERROR、从未成功调起。
 * 本测试守护它们保持可被 Quartz 反射实例化（有无参构造），防回潮到构造器注入。
 */
class LevelMetricCalcJobTest {

    @Test
    @DisplayName("Level1/2/3MetricCalcJob 必须有无参构造（Quartz 反射实例化前提）")
    void levelJobs_haveNoArgConstructor() {
        assertThatCode(() -> Level1MetricCalcJob.class.getDeclaredConstructor()).doesNotThrowAnyException();
        assertThatCode(() -> Level2MetricCalcJob.class.getDeclaredConstructor()).doesNotThrowAnyException();
        assertThatCode(() -> Level3MetricCalcJob.class.getDeclaredConstructor()).doesNotThrowAnyException();
    }
}
