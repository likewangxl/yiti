package com.bank.branch.platform.report.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Controller 层加强架构守护：禁止 controller 类依赖 entity 包任何类（对照 perf 的 V1.3 R4.1）.
 *
 * <p><b>背景</b>：既有 {@link RptNoEntityInControllerArchTest} 只守护 Controller
 * 方法的返回类型，无法检测方法体内把 entity 当作中间变量的用法
 * （如 {@code RptSavedQuery e = service.getById(...)} 后再装配 DTO）.
 *
 * <p><b>守护规则</b>：
 * <ul>
 *   <li>{@code com.bank.branch.platform.report.controller..} 下的任何类，
 *       不得依赖 {@code com.bank.branch.platform.report.entity..} 下的任何类（涵盖
 *       import、字段类型、方法局部变量、泛型参数、注解参数等所有 ArchUnit 可见依赖面）.</li>
 * </ul>
 *
 * <p><b>为什么禁止</b>：
 * <ol>
 *   <li>Controller 感知 entity 会让业务字段（审计列、数据库版本号、含敏感上下文的 paramsJson 等）
 *       暴露到装配层，DTO 隐藏字段的意义失效.</li>
 *   <li>Service/Facade 的内部模型（entity）和对外契约（DTO）耦合在 Controller 层，
 *       任何 DDL 变更都会波及 Controller.</li>
 *   <li>单元测试 / ArchUnit 无法区分"合法的返回类型 DTO"与"局部变量当成 entity 中转"两种写法，
 *       只有禁止 import 才能彻底杜绝.</li>
 * </ol>
 *
 * <p>与 {@link RptNoEntityInControllerArchTest} 互补：后者聚焦返回类型，本测试聚焦 import / 依赖.
 */
@AnalyzeClasses(
        packages = "com.bank.branch.platform.report",
        importOptions = {ImportOption.DoNotIncludeTests.class})
public class RptNoEntityInControllerLocalsArchTest {

    @ArchTest
    public static final ArchRule controllers_shouldNot_dependOn_entity_classes =
            noClasses()
                    .that().resideInAPackage("..report.controller..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..report.entity..")
                    .because("Controller 层不得感知 entity（包括 import、局部变量、字段、泛型）"
                          + "——对照 performance V1.3 R4.1 同款守护");
}
