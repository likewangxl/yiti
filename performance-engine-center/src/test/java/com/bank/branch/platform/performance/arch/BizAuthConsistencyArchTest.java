package com.bank.branch.platform.performance.arch;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizType;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * @BizAuth 单档策略架构守护测试.
 *
 * <p>守护规则：performance-engine-center 的所有 Controller 方法若标注了 {@code @BizAuth},
 * 其 {@code bizType} 必须为 {@link BizType#PERF_CONFIG}。
 *
 * <p>决策背景（2026-04-22）：不扩展 common-security 的 BizType 枚举；
 * 细粒度授权通过 {@code @BizAuth.action} + PT_RESOURCE 资源 ID 实现。
 *
 * <p>此为守护测试场景（不是红-绿-重构的 Red），首次运行即 PASS，验证了现有代码已符合单档约束。
 */
@AnalyzeClasses(packages = "com.bank.branch.platform.performance.controller")
public class BizAuthConsistencyArchTest {

    /**
     * 所有 Controller 方法上的 {@code @BizAuth} 注解，其 bizType 必须等于 {@code PERF_CONFIG}.
     */
    @ArchTest
    static final ArchRule allBizAuth_useSinglePerfConfig =
        methods()
          .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
          .and().arePublic()
          .and().areAnnotatedWith(BizAuth.class)
          .should(new ArchCondition<JavaMethod>("@BizAuth.bizType must be PERF_CONFIG") {
              @Override
              public void check(JavaMethod method, ConditionEvents events) {
                  BizAuth ann = method.reflect().getAnnotation(BizAuth.class);
                  if (ann.bizType() != BizType.PERF_CONFIG) {
                      events.add(SimpleConditionEvent.violated(method,
                          method.getFullName() + " 使用了非 PERF_CONFIG 的 BizType: " + ann.bizType()));
                  }
              }
          });
}
