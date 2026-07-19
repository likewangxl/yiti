package com.bank.branch.platform.performance.arch;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * V1.3 R4.1 新增架构守护：Controller 层不得依赖 entity 包的任何类
 * （包括但不限于 import、方法局部变量、字段、泛型参数）。
 *
 * <p><b>背景</b>：既有 {@link NoEntityInControllerArchTest} 只守护 Controller
 * 方法的 <em>返回类型</em>，无法检测方法体内把 entity 当作中间变量的用法
 * （如 {@code PerfMetricDef def = service.getByCode(...)} 后再装配 DTO）。
 * V1.0 Phase D reviewer 已标记 5 个 Controller 存在该瑕疵，V1.2 虽然已让返回
 * 类型全部切换到 DTO，但 import 和局部变量的 entity 依赖仍未清理。
 *
 * <p><b>守护规则</b>：
 * <ul>
 *   <li>{@code com.bank.branch.platform.performance.controller..} 下的任何类，
 *       不得依赖 {@code com.bank.branch.platform.performance.entity..} 下的任何类。
 *       此处"依赖"涵盖 ArchUnit 可见的全部引用面：import、字段类型、方法局部变量、
 *       throws、泛型参数、注解参数等。</li>
 * </ul>
 *
 * <p><b>为什么禁止</b>：
 * <ol>
 *   <li>Controller 感知 entity 会让业务字段（审计列、数据库版本号、含敏感上下文的
 *       paramsJson 等）暴露到装配层，DTO 隐藏字段的意义失效。</li>
 *   <li>Service/Facade 的内部模型（entity）和对外契约（DTO）耦合在 Controller 层，
 *       任何 DDL 变更都会波及 Controller。</li>
 *   <li>单元测试 / ArchUnit 无法区分 "合法的返回类型 DTO" 与 "局部变量当成 entity
 *       中转" 两种写法，只有禁止 import 才能彻底杜绝。</li>
 * </ol>
 *
 * <p><b>迁移建议</b>（踩到此守护时的修复路径）：
 * <ul>
 *   <li>Controller 方法体内的 entity 局部变量 → 改为 Facade/Service 层返回 DTO
 *       （新增 {@code xxxDTOBy...} 方法或让既有方法返回 DTO），Controller 仅负责
 *       入参校验 + 响应包装。</li>
 *   <li>Controller 的 DTO 装配（{@code Assembler.toDto(entity)}）→ 下沉到 Facade 层，
 *       Controller 不直接调 Assembler。</li>
 *   <li>{@code new PerfXxx()} 构造在 Controller 里 → 改为 Service 层接收 Cmd，
 *       Controller 只传 Cmd。</li>
 * </ul>
 *
 * <p><b>对照</b> {@link NoEntityInControllerArchTest} 聚焦返回类型；本测试聚焦
 * Controller 类级别的 import / 依赖，两者互补。
 *
 * <p><b>2026-07-19 修复</b>：原规则的包名匹配 {@code ..performance.controller..} /
 * {@code ..performance.entity..} 是按"相邻包段"做字面匹配的，无法覆盖 eval 子域
 * {@code performance.eval.controller} / {@code performance.eval.entity}
 * （中间多了一级 {@code eval}，不是相邻段）——已改为 {@code resideInAnyPackage}
 * 显式枚举两套子域的包路径。拓宽后 eval 侧多个 Controller 类暴露出对 entity
 * 的直接依赖（历史遗留，未走 DTO 装配），已通过 {@link #FROZEN_EXEMPT_CONTROLLERS}
 * 显式冻结为存量技术债——**只允许清单内的类沿用旧写法，新增违规一律禁止**。
 */
@AnalyzeClasses(
        packages = "com.bank.branch.platform.performance",
        importOptions = {ImportOption.DoNotIncludeTests.class})
public class NoEntityInControllerLocalsArchTest {

    /**
     * 存量技术债冻结清单（2026-07-19）.
     *
     * <p>与 {@link NoEntityInControllerArchTest#FROZEN_VIOLATIONS} 对应的类级别冻结——
     * 本测试按"类是否依赖 entity 包"判定，粒度是整个 Controller 类而非单个方法，
     * 故清单以全限定类名登记。后续如有余力应逐个改造为只依赖 DTO 并从本清单移除。
     */
    private static final Set<String> FROZEN_EXEMPT_CONTROLLERS = Set.of(
            "com.bank.branch.platform.performance.eval.controller.EvalAssignBatchController",
            "com.bank.branch.platform.performance.eval.controller.EvalRewardAdminController",
            "com.bank.branch.platform.performance.eval.controller.EvalRuleController",
            "com.bank.branch.platform.performance.eval.controller.EvalScoreController",
            "com.bank.branch.platform.performance.eval.controller.EvalTagController",
            "com.bank.branch.platform.performance.eval.controller.EvalTaskController",
            "com.bank.branch.platform.performance.eval.controller.EvalUserTagController"
    );

    private static final DescribedPredicate<JavaClass> NOT_FROZEN_EXEMPT =
            new DescribedPredicate<JavaClass>("not in 2026-07-19 frozen entity-locals exemption list") {
                @Override
                public boolean test(JavaClass input) {
                    return !FROZEN_EXEMPT_CONTROLLERS.contains(input.getFullName());
                }
            };

    /**
     * Controller 层不得依赖 entity 包（V1.3 加强守护；2026-07-19 扩到 eval 子域）.
     */
    @ArchTest
    public static final ArchRule controllers_shouldNot_dependOn_entity_classes =
            noClasses()
                    .that().resideInAnyPackage(
                            "..performance.controller..", "..performance.eval.controller..")
                    .and(NOT_FROZEN_EXEMPT)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..performance.entity..", "..performance.eval.entity..")
                    .because("Controller 层不得感知 entity（包括 import、局部变量、字段、泛型）"
                            + "V1.3 R4.1 加强守护，2026-07-19 扩到 eval 子域并冻结存量违规");
}
