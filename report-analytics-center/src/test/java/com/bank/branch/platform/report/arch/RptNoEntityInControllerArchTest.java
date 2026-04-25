package com.bank.branch.platform.report.arch;

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
 * Controller 层架构守护：Controller 返回类型不得暴露 entity（对照 performance 同名测试）.
 *
 * <p>守护规则：Controller 类的 public 方法不得直接返回 entity，
 * 也不得通过泛型（如 ResponseWrapper&lt;RptSavedQuery&gt;）暴露 entity 类型.
 * Controller 应通过 Assembler 将 entity 转换为 RespDTO 再返回.
 */
@AnalyzeClasses(packages = "com.bank.branch.platform.report")
public class RptNoEntityInControllerArchTest {

    private static final String ENTITY_PACKAGE_PREFIX =
            "com.bank.branch.platform.report.entity";

    @ArchTest
    public static final ArchRule controllers_shouldNot_expose_entity_types =
            methods()
                    .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
                    .and().arePublic()
                    .should(notExposeEntityInReturnType());
                    // M1.1+ 起 Controller 包至少有 MetaController 一个类，allowEmptyShould 守护已转为实质检查

    private static ArchCondition<JavaMethod> notExposeEntityInReturnType() {
        return new ArchCondition<JavaMethod>("return type must not reference entity package") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                JavaClass rawReturnType = method.getRawReturnType();
                if (isEntity(rawReturnType)) {
                    events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " 直接返回 entity: " + rawReturnType.getName()));
                    return;
                }
                method.getReturnType().getAllInvolvedRawTypes().forEach(raw -> {
                    if (isEntity(raw)) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " 返回类型泛型参数含 entity: " + raw.getName()));
                    }
                });
            }

            private boolean isEntity(JavaClass clazz) {
                return clazz.getPackageName().startsWith(ENTITY_PACKAGE_PREFIX);
            }
        };
    }
}
