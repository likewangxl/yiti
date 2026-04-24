package com.bank.branch.platform.performance.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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
 */
@AnalyzeClasses(
        packages = "com.bank.branch.platform.performance",
        importOptions = {ImportOption.DoNotIncludeTests.class})
public class NoEntityInControllerLocalsArchTest {

    /**
     * Controller 层不得依赖 entity 包（V1.3 加强守护）.
     */
    @ArchTest
    public static final ArchRule controllers_shouldNot_dependOn_entity_classes =
            noClasses()
                    .that().resideInAPackage("..performance.controller..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..performance.entity..")
                    .because("Controller 层不得感知 entity（包括 import、局部变量、字段、泛型）V1.3 R4.1 加强守护");
}
