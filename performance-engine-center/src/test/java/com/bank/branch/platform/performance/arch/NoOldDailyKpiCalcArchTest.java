package com.bank.branch.platform.performance.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构守护（V1.7）：代码库不再出现 DailyKpiCalcJob / DailyKpiCalcQuartzJob 类.
 *
 * <p>V1.7 取消"每日凌晨批量跑 KPI 方案"机制，改为指标级 Quartz 触发 + 事件驱动 KPI 重算.
 * 本测试守护未来不会有人误恢复这 2 个旧类.
 *
 * <p>规则使用 vacuously true 语义：如果目标类不存在，规则自动通过；
 * 若有人重新引入包含 "DailyKpiCalc" 的 public 类，测试将立即失败.
 */
@AnalyzeClasses(
        packages = "com.bank.branch.platform.performance",
        importOptions = {ImportOption.DoNotIncludeTests.class})
public class NoOldDailyKpiCalcArchTest {

    /**
     * 生产代码中不得存在类名含 "DailyKpiCalc" 的 public 类（V1.7 守护）.
     *
     * <p>allowEmptyShould(true)：当前类库中该类已不存在，规则应 vacuously true 通过；
     * 若有人重新引入该类，规则将立即检测到并失败.
     */
    @ArchTest
    public static final ArchRule daily_kpi_calc_classes_must_not_exist =
            noClasses()
                    .that().haveSimpleNameContaining("DailyKpiCalc")
                    .should().bePublic()
                    .allowEmptyShould(true)
                    .because("V1.7 已删除 DailyKpiCalcJob / DailyKpiCalcQuartzJob，不得再恢复任何 DailyKpiCalc* 类");
}
