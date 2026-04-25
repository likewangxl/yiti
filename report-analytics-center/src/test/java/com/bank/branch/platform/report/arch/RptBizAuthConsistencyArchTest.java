package com.bank.branch.platform.report.arch;

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
 * @BizAuth 单档策略架构守护测试（对照 performance 的 BizAuthConsistencyArchTest）.
 *
 * <p>守护规则：report-analytics-center 的所有 Controller 方法若标注了 {@code @BizAuth},
 * 其 {@code bizType} 必须为 {@link BizType#REPORT}.
 *
 * <p>决策背景：common-security 的 BizType 枚举已有 {@code REPORT("REPORT", "报表分析")} 档，
 * report 模块不扩展新 BizType；细粒度授权通过 {@code @BizAuth.action} + PT_RESOURCE 资源 ID 实现.
 *
 * <p>M0 阶段 controller 包尚无 Controller 类，本规则 vacuously pass；M1+ 接入真 Controller 时生效.
 */
@AnalyzeClasses(packages = "com.bank.branch.platform.report.controller")
public class RptBizAuthConsistencyArchTest {

    /**
     * 所有 Controller 方法上的 {@code @BizAuth} 注解，其 bizType 必须等于 {@code REPORT}.
     */
    @ArchTest
    static final ArchRule allBizAuth_useSingleReport =
        methods()
          .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
          .and().arePublic()
          .and().areAnnotatedWith(BizAuth.class)
          .should(new ArchCondition<JavaMethod>("@BizAuth.bizType must be REPORT") {
              @Override
              public void check(JavaMethod method, ConditionEvents events) {
                  BizAuth ann = method.reflect().getAnnotation(BizAuth.class);
                  if (ann.bizType() != BizType.REPORT) {
                      events.add(SimpleConditionEvent.violated(method,
                          method.getFullName() + " 使用了非 REPORT 的 BizType: " + ann.bizType()));
                  }
              }
          });
    // M1.1+ 起 Controller 包至少有 MetaController 一个类，allowEmptyShould 守护已转为实质检查
}
