package com.bank.branch.platform.performance.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Controller 层架构守护测试：禁止 Controller 返回 entity 包下的类型.
 *
 * <p>守护规则：Controller 类的 public 方法不得直接返回 entity，
 * 也不得通过泛型（如 ResponseWrapper&lt;PerfMetricDef&gt;）暴露 entity 类型。
 *
 * <p>正确做法：Controller 应通过 Assembler 将 entity 转换为 RespDTO 再返回。
 */
@AnalyzeClasses(packages = "com.bank.branch.platform.performance")
public class NoEntityInControllerArchTest {

    private static final String ENTITY_PACKAGE_PREFIX =
            "com.bank.branch.platform.performance.entity.";

    /**
     * Controller 类中的 public 方法不得返回 entity 包下的类型，
     * 包括直接返回和作为 ResponseWrapper / PageResult 等容器的泛型参数。
     */
    @ArchTest
    public static final ArchRule controllers_shouldNot_expose_entity_types =
            methods()
                    .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
                    .and().arePublic()
                    .should(notExposeEntityInReturnType());

    private static ArchCondition<JavaMethod> notExposeEntityInReturnType() {
        return new ArchCondition<JavaMethod>("return type must not reference entity package") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                // 1. 直接返回类型
                JavaClass rawReturnType = method.getRawReturnType();
                if (isEntity(rawReturnType)) {
                    events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " 直接返回 entity: " + rawReturnType.getName()));
                    return;
                }
                // 2. 泛型参数中的类型（ResponseWrapper<PerfMetricDef> / PageResult<SysControl> 等）
                method.getReturnType().getAllInvolvedRawTypes().forEach(raw -> {
                    if (isEntity(raw)) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " 返回类型泛型参数含 entity: " + raw.getName()));
                    }
                });
            }

            private boolean isEntity(JavaClass clazz) {
                return clazz.getPackageName().startsWith(
                        "com.bank.branch.platform.performance.entity");
            }
        };
    }
}
