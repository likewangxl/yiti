package com.bank.branch.platform.portal.arch;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * @BizAuth 存在性架构守护测试（对照 performance 的 BizAuthConsistencyArchTest /
 * report 的 RptBizAuthConsistencyArchTest）。
 *
 * <p>背景（2026-07-19）：{@code AnnouncementController} 的全部 9 个端点（J.1-J.9）
 * 曾完全没有标注 {@code @BizAuth}——对应的 9 个 {@code RES_ANN_*} 资源虽已在
 * {@code PT_RESOURCE} 完整登记（见 {@code docs/superpowers/sql/2026-05-25-create-announcement-table.sql}
 * 与 {@code docs/superpowers/sql/2026-06-03-announcement-admin-ops-sysadmin-only.sql}），
 * 但由于方法未标注注解，{@code AuthorizationInterceptor} Step3（{@code BizMetaResolver}
 * 解析 {@code @BizAuth}）直接放行并退化为仅含 {@code empId}/{@code orgCode} 的最小
 * {@code DataScopeContext}，不解析 {@code BizType}/{@code BizAction}——RBAC 本身（Step1
 * 资源匹配 + Step2 角色-资源绑定）不受影响，但该资源登记的 bizType 分类形同虚设。
 * 本规则把"Controller 公共处理方法必须标注 @BizAuth"从口头约定升级为架构守护，
 * 防止同类回归再次滑入生产。
 *
 * <p>豁免白名单（{@link #NO_AUTH_ALLOWLIST}）：{@code ShortcutController}
 * （工作台快捷入口）与 {@code WorkspaceController}（工作台聚合）是仅需登录态、
 * 无独立业务权限维度的用户级接口，源码类级 Javadoc 已明确声明"不需要 @BizAuth，
 * 登录即可访问"，本规则显式排除这两个类的全部方法；除此之外的 controller 包
 * 所有 public 处理方法一律必须标注 {@code @BizAuth}。
 */
@AnalyzeClasses(packages = "com.bank.branch.platform.portal.controller")
public class PortalBizAuthArchTest {

    /**
     * 显式豁免：仅需登录态即可访问、无业务权限维度的接口（{@code 类名#方法名}）。
     * ShortcutController / WorkspaceController 的类级 Javadoc 已说明设计意图，
     * 不属于遗漏，因此不计入"必须标注 @BizAuth"的强制覆盖率校验。
     */
    private static final Set<String> NO_AUTH_ALLOWLIST = Set.of(
            "ShortcutController#listShortcuts",
            "ShortcutController#saveShortcuts",
            "WorkspaceController#getWorkspace"
    );

    /**
     * portal-content-center 的 controller 包下所有 public 处理方法必须标注 {@code @BizAuth}，
     * {@link #NO_AUTH_ALLOWLIST} 中登记的登录态豁免方法除外。
     */
    @ArchTest
    static final ArchRule allControllerMethods_mustHaveBizAuthUnlessAllowlisted =
        methods()
          .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
          .and().arePublic()
          .should(new ArchCondition<JavaMethod>("声明 @BizAuth 注解（登录态豁免白名单除外）") {
              @Override
              public void check(JavaMethod method, ConditionEvents events) {
                  boolean hasBizAuth = method.reflect().getAnnotation(BizAuth.class) != null;
                  if (hasBizAuth) {
                      return;
                  }
                  String key = method.getOwner().getSimpleName() + "#" + method.getName();
                  if (NO_AUTH_ALLOWLIST.contains(key)) {
                      return;
                  }
                  events.add(SimpleConditionEvent.violated(method,
                      method.getFullName() + " 缺少 @BizAuth 注解，且未在登录态豁免白名单中"));
              }
          });
}
