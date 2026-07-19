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
 *
 * <p><b>2026-07-19 修复</b>：{@code isEntity()} 原先硬编码前缀
 * {@code com.bank.branch.platform.performance.entity.}，无法识别
 * eval 子域的 {@code com.bank.branch.platform.performance.eval.entity}
 * 包（两者是同级包，不是父子关系，字符串前缀不匹配）——导致 eval 控制器
 * 直接返回 {@code EvalTag}/{@code EvalRule}/{@code EvalTask} 等实体的现状
 * 长期未被本测试捕获。现改为按包名分段匹配任意名为 {@code entity} 的段，
 * 不再依赖具体子域前缀。已发现的存量违规见 {@link #FROZEN_VIOLATIONS}。
 */
@AnalyzeClasses(packages = "com.bank.branch.platform.performance")
public class NoEntityInControllerArchTest {

    /**
     * 存量技术债冻结清单（2026-07-19）.
     *
     * <p>拓宽 {@code isEntity()} 识别范围后，eval 子域多个 Controller 方法
     * 暴露出直接返回 entity 的历史写法（未走 DTO 装配）。本次修复的目标是
     * 堵住"架构测试盲区"本身，不是借机做一次大范围 DTO 化重构，故显式冻结
     * 以下方法为已知技术债——**只允许清单内方法沿用旧写法，新增违规一律禁止**。
     * 后续如有余力应逐个改造为返回 DTO 并从本清单移除。
     *
     * <p>格式：{@code 全限定方法签名}（{@link JavaMethod#getFullName()}）。
     */
    private static final java.util.Set<String> FROZEN_VIOLATIONS = java.util.Set.of(
            "com.bank.branch.platform.performance.eval.controller.EvalAssignBatchController.list(java.lang.Integer, java.lang.String, int, int)",
            "com.bank.branch.platform.performance.eval.controller.EvalAssignBatchController.publish(java.lang.Long)",
            "com.bank.branch.platform.performance.eval.controller.EvalRewardAdminController.batches(java.lang.Integer, java.lang.String, int, int)",
            "com.bank.branch.platform.performance.eval.controller.EvalRewardAdminController.publish(java.lang.Long)",
            "com.bank.branch.platform.performance.eval.controller.EvalRuleController.create(com.bank.branch.platform.performance.eval.dto.CreateRuleReq)",
            "com.bank.branch.platform.performance.eval.controller.EvalRuleController.list(java.lang.String, int, int)",
            "com.bank.branch.platform.performance.eval.controller.EvalScoreController.myTasks(int, int)",
            "com.bank.branch.platform.performance.eval.controller.EvalScoreController.targets(java.lang.Long)",
            "com.bank.branch.platform.performance.eval.controller.EvalTagController.create(java.lang.String)",
            "com.bank.branch.platform.performance.eval.controller.EvalTagController.list(java.lang.String, int, int)",
            "com.bank.branch.platform.performance.eval.controller.EvalTagController.listAll(java.lang.Integer)",
            "com.bank.branch.platform.performance.eval.controller.EvalTaskController.create(com.bank.branch.platform.performance.eval.dto.CreateTaskReq)",
            "com.bank.branch.platform.performance.eval.controller.EvalTaskController.list(java.lang.Integer, java.lang.String, int, int)",
            "com.bank.branch.platform.performance.eval.controller.EvalUserTagController.list(java.lang.String)"
    );

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
                // 2026-07-19：冻结清单内的存量违规不再重复上报（见类注释 FROZEN_VIOLATIONS）
                if (FROZEN_VIOLATIONS.contains(method.getFullName())) {
                    return;
                }
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
                // 2026-07-19 修复：原先仅判断字符串前缀 "com...performance.entity"，
                // 无法识别同级包 "com...performance.eval.entity"（eval 子域实体）。
                // 改为按包名分段精确匹配名为 "entity" 的段，覆盖任意深度的 entity 子包。
                String pkg = clazz.getPackageName();
                if (!pkg.startsWith("com.bank.branch.platform.performance.")) {
                    return false;
                }
                for (String segment : pkg.split("\\.")) {
                    if (segment.equals("entity")) {
                        return true;
                    }
                }
                return false;
            }
        };
    }
}
