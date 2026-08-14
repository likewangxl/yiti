package com.bank.branch.platform.performance.arch;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * 架构守护测试（2026-07-19 新增）：所有 Controller 的 HTTP 处理方法必须声明 {@code @BizAuth}.
 *
 * <p><b>背景</b>：既有 {@link BizAuthConsistencyArchTest} 只校验"若声明了 @BizAuth，
 * 其 bizType 必须落在单档约束内"，从未要求"必须声明"——这正是
 * {@code MetricBatchCalcController.trigger} 长期未标注 @BizAuth 却未被任何架构测试
 * 拦截的根因（该测试的 {@code @AnalyzeClasses} 虽然扫到了这个类，但规则本身对"零声明"
 * 天然放行）。同时 {@code BizAuthConsistencyArchTest} 的扫描包硬编码为
 * {@code performance.controller}，不覆盖 {@code performance.eval.controller}，也是本次
 * 一并修复的盲区之一——本测试改为同时扫描两个包。
 *
 * <p><b>为什么不直接把 eval.controller 并入 BizAuthConsistencyArchTest 的扫描范围</b>：
 * 该测试断言"bizType 必须是 PERF_CONFIG 或 KPI_CALC"，而 eval 子域按模块 AGENTS.md
 * 记载的既定设计统一使用 {@code BizType.EVAL}，是刻意分裂的两套体系，合并扫描会把
 * eval 的合法用法误判为违规。因此"必须声明 @BizAuth"这条新规则单独成测，扫描范围可以
 * 覆盖两个包，而 bizType 取值的单档校验规则保持不动。
 *
 * <p><b>判定口径</b>：只对标注了 Spring MVC 路由注解（{@code @GetMapping}/
 * {@code @PostMapping}/{@code @PutMapping}/{@code @DeleteMapping}/{@code @PatchMapping}/
 * {@code @RequestMapping}）的 public 方法生效——即真正对外暴露的 HTTP 端点；非路由的
 * public 辅助方法不做要求。
 *
 * <p><b>已知例外</b>：{@code DataTaskController.reportStatus}（{@code POST
 * /api/data-task/status}）不标注 @BizAuth，见 {@link #EXEMPT_METHODS} 的详细说明。
 */
@AnalyzeClasses(packages = {
        "com.bank.branch.platform.performance.controller",
        "com.bank.branch.platform.performance.eval.controller"
})
public class BizAuthRequiredArchTest {

    /**
     * 显式豁免清单.
     *
     * <p>{@code DataTaskController.reportStatus}：源码 Javadoc（03 §G.1）称该端点走
     * "外部 API Token"鉴权、不走前端登录态，因此不标 @BizAuth。经核实，仓库内实际
     * 并<b>未</b>实现任何 {@code X-Api-Token}/{@code sys_api_token} 校验逻辑——该端点
     * 实际仍受 {@code auth-permission-center} 的 {@code AuthenticationFilter}
     * （Session 校验）与 {@code AuthorizationInterceptor}（{@code PT_RESOURCE} 注册 +
     * RBAC 角色-资源绑定）保护，只是跳过了 {@code @BizAuth} 对应的细粒度 BizType/
     * DataScope 校验这一层。这是一处文档与代码不完全一致的历史遗留（"API Token"是尚未
     * 落地的设计），但"不标 @BizAuth"本身仍是当前刻意的架构选择（外部系统回调场景没有
     * 前端会话意义上的数据范围概念），故在此显式豁免而非当作本次要修的缺口——是否要
     * 补齐真实的 API Token 机制或者把该端点也补上 @BizAuth，需要产品/架构侧另行决策，
     * 不在本次修复范围内。
     */
    private static final Set<String> EXEMPT_METHODS = Set.of(
            "com.bank.branch.platform.performance.controller.DataTaskController.reportStatus("
                    + "com.bank.branch.platform.performance.controller.dto.DataTaskStatusReqDTO)"
    );

    @SafeVarargs
    private static boolean isHttpHandler(Method reflectMethod, Class<? extends Annotation>... mappingAnnotations) {
        for (Class<? extends Annotation> anno : mappingAnnotations) {
            if (reflectMethod.isAnnotationPresent(anno)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Controller 的所有 HTTP 处理方法必须声明 {@code @BizAuth}（豁免清单除外）.
     */
    @ArchTest
    static final ArchRule allHandlerMethods_mustDeclare_bizAuth =
            methods()
                    .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
                    .and().arePublic()
                    .should(new ArchCondition<JavaMethod>("declare @BizAuth when mapped as an HTTP endpoint") {
                        @Override
                        public void check(JavaMethod method, ConditionEvents events) {
                            Method reflectMethod = method.reflect();
                            boolean handler = isHttpHandler(reflectMethod,
                                    GetMapping.class, PostMapping.class, PutMapping.class,
                                    DeleteMapping.class, PatchMapping.class, RequestMapping.class);
                            if (!handler) {
                                // 非路由方法（未映射 HTTP 端点的 public 辅助方法）不做要求
                                return;
                            }
                            if (EXEMPT_METHODS.contains(method.getFullName())) {
                                return;
                            }
                            if (!reflectMethod.isAnnotationPresent(BizAuth.class)) {
                                events.add(SimpleConditionEvent.violated(method,
                                        method.getFullName() + " 是 HTTP 处理方法但未声明 @BizAuth"
                                                + "（未登记豁免清单，需补注解或加入 EXEMPT_METHODS 并说明理由）"));
                            }
                        }
                    });
}
