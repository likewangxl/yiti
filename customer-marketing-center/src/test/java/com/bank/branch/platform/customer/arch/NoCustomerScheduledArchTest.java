package com.bank.branch.platform.customer.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * V1.8 — customer-marketing-center 模块 ArchUnit 守护：禁止 @Scheduled。
 * <p>
 * V1.8 起业务定时调度全部走 Quartz（job_key=LEAD_CALLBACK_COMPENSATE
 * 由 sys_job_conf 注册），customer 模块不应再出现 Spring {@code @Scheduled}。
 * </p>
 * <p>
 * <strong>注</strong>：若未来 customer 模块出现合理的 {@code @Scheduled}
 * 需求，应当先评估是否真的合理（例如纯本地兜底巡检），并在评估通过后
 * 调整本规则；不允许通过本测试静默回退。
 * </p>
 */
@AnalyzeClasses(
        packages = "com.bank.branch.platform.customer",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class NoCustomerScheduledArchTest {

    @ArchTest
    static final ArchRule no_spring_scheduled =
            noMethods()
                    .should()
                    .beAnnotatedWith("org.springframework.scheduling.annotation.Scheduled")
                    .as("V1.8 起 customer 模块禁止 @Scheduled —— 业务调度走 Quartz")
                    .allowEmptyShould(true);
}
